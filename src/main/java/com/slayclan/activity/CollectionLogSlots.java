package com.slayclan.activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import net.runelite.client.game.ItemManager;

/**
 * The game's collection log slots, read from the cache the way WikiSync does: the tabs (enum 2102) point to their
 * pages, each page to the enum of its items, and enum 3721 swaps some ids for the id the log shows.
 * <p>
 * The cache can list one slot under two ids on two pages: the Prospector kit is 12013-12016 on the Motherlode Mine
 * page and 29472-29478 on the Volcanic Mine page, and the game sends both while its own count holds one slot. Nothing
 * in the cache links the two, so ids that look exactly alike and never share a page count as one slot, under the
 * lowest id. Same-name slots that look different (the Graceful sets) or share a page (Ancient and Mysterious pages)
 * stay apart. Checked on every cache from 2024-10 to 2026-11: only the Prospector kit merges, and the slot count
 * equals the game's own total each time.
 */
public class CollectionLogSlots
{
	// Neither gameval nor net.runelite.api names these enums and params; the ids come from WikiSync and RuneProfile
	static final int COLLECTION_LOG_TABS = 2102;
	static final int TAB_PAGES_PARAM = 683;
	static final int PAGE_ITEMS_PARAM = 690;
	static final int ITEM_REPLACEMENTS = 3721;

	private final Map<Integer, Integer> replacements;
	private final Map<Integer, Integer> slotOfId;

	private CollectionLogSlots(Map<Integer, Integer> replacements, Map<Integer, Integer> slotOfId)
	{
		this.replacements = replacements;
		this.slotOfId = slotOfId;
	}

	public static CollectionLogSlots read(Client client, ItemManager itemManager)
	{
		Map<Integer, Integer> replacements = replacementIds(client);
		Map<Integer, Set<Integer>> pagesOfId = pagesOfLogIds(client, replacements);
		return new CollectionLogSlots(replacements, slotsOf(pagesOfId, itemManager));
	}

	/**
	 * The slot a transmitted item counts for, or null when it is in no slot. Without the cache every id is its own
	 * slot.
	 */
	public Integer slotOf(int itemId)
	{
		int id = replacements.getOrDefault(itemId, itemId);
		return slotOfId.isEmpty() ? Integer.valueOf(id) : slotOfId.get(id);
	}

	public boolean isKnown()
	{
		return !slotOfId.isEmpty();
	}

	public int count()
	{
		return new HashSet<>(slotOfId.values()).size();
	}

	private static Map<Integer, Integer> replacementIds(Client client)
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

	/**
	 * Empty when any part of the log is missing from the cache, for example after a game update moved a param, so the
	 * capture then falls back to one slot per id instead of trusting half a log.
	 */
	private static Map<Integer, Set<Integer>> pagesOfLogIds(Client client, Map<Integer, Integer> replacements)
	{
		Map<Integer, Set<Integer>> pagesOfId = new TreeMap<>();
		int[] tabs = enumValues(client, COLLECTION_LOG_TABS);
		if (tabs == null)
		{
			return Map.of();
		}
		for (int tab : tabs)
		{
			int[] pages = enumValues(client, structParam(client, tab, TAB_PAGES_PARAM));
			if (pages == null)
			{
				return Map.of();
			}
			for (int page : pages)
			{
				int[] itemIds = enumValues(client, structParam(client, page, PAGE_ITEMS_PARAM));
				if (itemIds == null)
				{
					return Map.of();
				}
				for (int itemId : itemIds)
				{
					pagesOfId.computeIfAbsent(replacements.getOrDefault(itemId, itemId), id -> new HashSet<>()).add(page);
				}
			}
		}
		return pagesOfId;
	}

	private static int[] enumValues(Client client, int enumId)
	{
		EnumComposition values = enumId < 0 ? null : client.getEnum(enumId);
		return values == null ? null : values.getIntVals();
	}

	private static int structParam(Client client, int structId, int param)
	{
		StructComposition struct = client.getStructComposition(structId);
		return struct == null ? -1 : struct.getIntValue(param);
	}

	private static Map<Integer, Integer> slotsOf(Map<Integer, Set<Integer>> pagesOfId, ItemManager itemManager)
	{
		Map<String, List<Integer>> idsByName = new HashMap<>();
		for (int id : pagesOfId.keySet())
		{
			idsByName.computeIfAbsent(itemManager.getItemComposition(id).getMembersName(), name -> new ArrayList<>()).add(id);
		}
		Map<Integer, Integer> slotOfId = new HashMap<>();
		for (List<Integer> sameName : idsByName.values())
		{
			assignSlots(sameName, pagesOfId, itemManager, slotOfId);
		}
		return slotOfId;
	}

	// Ids come in ascending order, so each slot is named by its lowest id
	private static void assignSlots(List<Integer> sameName, Map<Integer, Set<Integer>> pagesOfId, ItemManager itemManager,
		Map<Integer, Integer> slotOfId)
	{
		Map<Integer, Set<Integer>> pagesOfSlot = new HashMap<>();
		for (int id : sameName)
		{
			Set<Integer> pages = pagesOfId.get(id);
			Integer slot = pagesOfSlot.keySet().stream()
				.filter(candidate -> isSameSlot(itemManager, candidate, pagesOfSlot.get(candidate), id, pages))
				.min(Integer::compare)
				.orElse(id);
			pagesOfSlot.computeIfAbsent(slot, s -> new HashSet<>()).addAll(pages);
			slotOfId.put(id, slot);
		}
	}

	private static boolean isSameSlot(ItemManager itemManager, int slot, Set<Integer> slotPages, int id, Set<Integer> pages)
	{
		return Collections.disjoint(slotPages, pages) && looksAlike(itemManager.getItemComposition(slot), itemManager.getItemComposition(id));
	}

	private static boolean looksAlike(ItemComposition a, ItemComposition b)
	{
		return a.getInventoryModel() == b.getInventoryModel()
			&& Arrays.equals(a.getColorToReplace(), b.getColorToReplace())
			&& Arrays.equals(a.getColorToReplaceWith(), b.getColorToReplaceWith())
			&& Arrays.equals(a.getTextureToReplace(), b.getTextureToReplace())
			&& Arrays.equals(a.getTextureToReplaceWith(), b.getTextureToReplaceWith());
	}
}
