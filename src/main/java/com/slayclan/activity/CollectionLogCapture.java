package com.slayclan.activity;

import com.slayclan.task.Names;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Captures the full collection log the way RuneProfile and WikiSync do: when the player opens their own log,
 * the game's search is triggered, which makes the server send one script 4100 call per owned item.
 */
@Slf4j
public class CollectionLogCapture
{
	// Neither gameval nor net.runelite.api.ScriptID names these scripts and this enum; the ids come from WikiSync and RuneProfile
	public static final int COLLECTION_LOG_SETUP = 7797;
	public static final int COLLECTION_TRANSMIT = 4100;
	static final int COLLECTION_INIT = 2240;
	static final int ITEM_REPLACEMENTS = 3721;
	private static final int SETTLE_TICKS = 2;
	// A capture that gets no items, because the log was closed at once or the search did not open, must not swallow the next opening
	static final int NO_ITEMS_TIMEOUT_TICKS = 50;

	private final Client client;
	private final ItemManager itemManager;
	private final BooleanSupplier captureWanted;
	private boolean captureAllowed;
	private boolean capturing;
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
		reset();
	}

	/**
	 * Lets the next opening of the log capture again, for when the captured list could not be used.
	 */
	public void allowAgain()
	{
		captureAllowed = true;
	}

	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != COLLECTION_LOG_SETUP || !shouldStartCapture())
		{
			return;
		}
		captureAllowed = false;
		capturing = true;
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
		items.add(new OwnedItem(itemId, itemManager.getItemComposition(itemId).getName(), quantity));
		lastTransmitTick = client.getTickCount();
	}

	public Optional<List<OwnedItem>> onTick(int tick)
	{
		if (captureGaveNothing(tick))
		{
			log.debug("Collection log capture got no items, allowing the next opening to try again");
			reset();
			captureAllowed = true;
			return Optional.empty();
		}
		if (!captureSettled(tick))
		{
			return Optional.empty();
		}
		List<OwnedItem> captured = distinctItems();
		reset();
		return Optional.of(captured);
	}

	private void reset()
	{
		capturing = false;
		items.clear();
		captureStartTick = -1;
		lastTransmitTick = -1;
	}

	/**
	 * The game sends an item once per log page it appears on, and some items have a replacement id (enum 3721,
	 * as WikiSync uses it), so the list is folded to one entry per item, matching the game's own slot count.
	 * Items the enum does not cover but which the game still sends under two ids, such as the Prospector set
	 * (12013 and 29472), share one name, so a second pass folds on the name and keeps the lowest id.
	 */
	private List<OwnedItem> distinctItems()
	{
		Map<Integer, Integer> replacements = replacementIds();
		log.debug("Collection log capture folding {} items with {} replacement ids", items.size(), replacements.size());
		Map<Integer, OwnedItem> byId = new LinkedHashMap<>();
		for (OwnedItem item : items)
		{
			int id = replacements.getOrDefault(item.getId(), item.getId());
			if (higherQuantityThanKnown(item, byId.get(id)))
			{
				byId.put(id, new OwnedItem(id, item.getName(), item.getQuantity()));
			}
		}
		Map<String, OwnedItem> byName = new LinkedHashMap<>();
		for (OwnedItem item : byId.values())
		{
			String name = Names.normalize(item.getName());
			OwnedItem known = byName.get(name);
			if (known == null)
			{
				byName.put(name, item);
			}
			else
			{
				byName.put(name, new OwnedItem(Math.min(known.getId(), item.getId()), known.getName(),
					Math.max(known.getQuantity(), item.getQuantity())));
			}
		}
		return new ArrayList<>(byName.values());
	}

	private static boolean higherQuantityThanKnown(OwnedItem item, OwnedItem known)
	{
		return known == null || item.getQuantity() > known.getQuantity();
	}

	private Map<Integer, Integer> replacementIds()
	{
		EnumComposition replacements = client.getEnum(ITEM_REPLACEMENTS);
		Map<Integer, Integer> ids = new HashMap<>();
		if (replacements == null)
		{
			return ids;
		}
		int[] from = replacements.getKeys();
		int[] to = replacements.getIntVals();
		for (int i = 0; i < Math.min(from.length, to.length); i++)
		{
			ids.put(from[i], to[i]);
		}
		return ids;
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
