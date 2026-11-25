package com.slayclan.activity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Captures the full collection log the way RuneProfile and WikiSync do: when the player opens their own log,
 * the game's search is triggered, which makes the server send one script 4100 call per owned item.
 */
@Slf4j
public class CollectionLogCapture
{
	// Neither gameval nor net.runelite.api.ScriptID names these scripts; the ids come from WikiSync and RuneProfile
	public static final int COLLECTION_LOG_SETUP = 7797;
	public static final int COLLECTION_TRANSMIT = 4100;
	static final int COLLECTION_INIT = 2240;
	private static final int SETTLE_TICKS = 2;
	// A capture that gets no items, because the log was closed at once or the search did not open, must not swallow the next opening
	static final int NO_ITEMS_TIMEOUT_TICKS = 50;
	// An incomplete capture is retried at the next openings, but a log that never adds up must not toggle the search every time
	static final int MAX_CAPTURES_PER_LOGIN = 3;

	private final Client client;
	private final ItemManager itemManager;
	private final BooleanSupplier captureWanted;
	private boolean captureAllowed;
	private boolean capturing;
	private int capturesThisLogin;
	private CollectionLogSlots slotTable;
	private final List<OwnedItem> items = new ArrayList<>();
	private int captureStartTick = -1;
	private int lastTransmitTick = -1;

	public CollectionLogCapture(Client client, ItemManager itemManager, BooleanSupplier captureWanted)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.captureWanted = captureWanted;
	}

	public void onLogin()
	{
		captureAllowed = true;
		capturesThisLogin = 0;
		slotTable = null;
		reset();
	}

	/**
	 * Lets the next opening of the log capture again, for when the captured list could not be used, within the
	 * captures allowed per login.
	 */
	public void allowAgain()
	{
		retryAtNextOpening();
	}

	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != COLLECTION_LOG_SETUP || !shouldStartCapture())
		{
			return;
		}
		captureAllowed = false;
		capturing = true;
		capturesThisLogin++;
		captureStartTick = client.getTickCount();
		client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
		client.runScript(COLLECTION_INIT);
	}

	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != COLLECTION_TRANSMIT || !capturing)
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		int itemId = (int) args[1];
		int quantity = (int) args[2];
		items.add(new OwnedItem(itemId, itemManager.getItemComposition(itemId).getMembersName(), quantity));
		lastTransmitTick = client.getTickCount();
	}

	public Optional<CapturedLog> onTick(int tick)
	{
		if (captureGaveNothing(tick))
		{
			log.debug("Collection log capture got no items, allowing the next opening to try again");
			reset();
			retryAtNextOpening();
			return Optional.empty();
		}
		if (!captureSettled(tick))
		{
			return Optional.empty();
		}
		// Reset first, so a capture that fails while folding is not retried on every tick
		List<OwnedItem> transmitted = new ArrayList<>(items);
		reset();
		CapturedLog captured = new CapturedLog(slotItems(transmitted), client.getVarpValue(VarPlayerID.COLLECTION_COUNT));
		if (captured.getGameCount() == 0)
		{
			// Another opening would not help until the game sends its count, which comes with Character summary
			log.debug("Collection log capture holds {} slots, the game has not sent its count", captured.getItems().size());
		}
		else if (!captured.isComplete())
		{
			log.info("Collection log capture holds {} slots while the game counts {}", captured.getItems().size(), captured.getGameCount());
			retryAtNextOpening();
		}
		return Optional.of(captured);
	}

	private void retryAtNextOpening()
	{
		if (capturesThisLogin < MAX_CAPTURES_PER_LOGIN)
		{
			captureAllowed = true;
		}
	}

	private void reset()
	{
		capturing = false;
		items.clear();
		captureStartTick = -1;
		lastTransmitTick = -1;
	}

	/**
	 * One entry per log slot, as the game counts them. The game sends an item once per page it appears on, and the
	 * Prospector kit under a second id (see {@link CollectionLogSlots}).
	 */
	private List<OwnedItem> slotItems(List<OwnedItem> transmitted)
	{
		CollectionLogSlots slots = slotTable();
		logWhenCacheDisagreesWithGame(slots);
		Map<Integer, OwnedItem> bySlot = new LinkedHashMap<>();
		int onNoPage = 0;
		for (OwnedItem item : transmitted)
		{
			Integer slot = slots.slotOf(item.getId());
			if (slot == null)
			{
				// Kept, so the item still counts for tasks; the capture then no longer matches the game and is marked so
				log.debug("Collection log item {} ({}) is on no log page in the cache", item.getId(), item.getName());
				onNoPage++;
				slot = item.getId();
			}
			if (higherQuantityThanKnown(item, bySlot.get(slot)))
			{
				bySlot.put(slot, new OwnedItem(slot, itemManager.getItemComposition(slot).getMembersName(), item.getQuantity()));
			}
		}
		if (onNoPage > 0)
		{
			log.info("Collection log capture kept {} items that are on no log page in the cache", onNoPage);
		}
		return new ArrayList<>(bySlot.values());
	}

	// The slots only change with the game cache, so they are read once per login
	private CollectionLogSlots slotTable()
	{
		if (slotTableNeedsReading())
		{
			long start = System.nanoTime();
			slotTable = readSlotTable();
			log.debug("Collection log slots read in {} ms", (System.nanoTime() - start) / 1_000_000);
		}
		return slotTable;
	}

	// Read again while the cache had no log, which happens when a capture runs before the cache is loaded
	private boolean slotTableNeedsReading()
	{
		return slotTable == null || !slotTable.isKnown();
	}

	// A cache the client cannot read the log from falls back to one slot per id rather than losing the capture
	private CollectionLogSlots readSlotTable()
	{
		try
		{
			return CollectionLogSlots.read(client, itemManager);
		}
		catch (RuntimeException e)
		{
			log.info("Collection log slots could not be read from the cache", e);
			return CollectionLogSlots.unknown();
		}
	}

	// A new pair of look-alike ids the slot rule does not cover would show up here first
	private void logWhenCacheDisagreesWithGame(CollectionLogSlots slots)
	{
		int gameTotal = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (cacheDisagreesWithGame(slots, gameTotal))
		{
			log.info("Collection log cache holds {} slots while the game counts {}", slots.count(), gameTotal);
		}
	}

	private static boolean cacheDisagreesWithGame(CollectionLogSlots slots, int gameTotal)
	{
		return slots.isKnown() && gameTotal > 0 && slots.count() != gameTotal;
	}

	private static boolean higherQuantityThanKnown(OwnedItem item, OwnedItem known)
	{
		return known == null || item.getQuantity() > known.getQuantity();
	}

	private boolean shouldStartCapture()
	{
		return captureAllowed && !capturing && captureWanted.getAsBoolean() && isOwnLog();
	}

	private boolean isOwnLog()
	{
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 0;
	}

	private boolean captureSettled(int tick)
	{
		return capturing && lastTransmitTick != -1 && tick - lastTransmitTick >= SETTLE_TICKS;
	}

	private boolean captureGaveNothing(int tick)
	{
		return capturing && lastTransmitTick == -1 && tick - captureStartTick > NO_ITEMS_TIMEOUT_TICKS;
	}
}
