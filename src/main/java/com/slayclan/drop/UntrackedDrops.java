package com.slayclan.drop;

import com.slayclan.task.Names;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * New collection log items and reward container items each wait a few ticks for a Loot Tracker report of the same
 * item, which then wins. Whichever of the three sees an item first, it is posted once.
 */
public class UntrackedDrops
{
	public static final String COLLECTION_LOG_SOURCE = "Collection log";
	// A Loot Tracker report that comes before the line is remembered all login, so this wait is only for one that
	// follows it: a chest whose loot the Loot Tracker reads from the inventory change a few ticks later. Content where
	// the line leads by longer, like Chambers of Xeric, never falls back at all
	public static final int COLLECTION_LOG_WAIT_TICKS = 10;
	// A container's reward and the Loot Tracker's report of a chest it knows come from the same inventory change,
	// which other plugins may handle a tick apart
	public static final int CONTAINER_WAIT_TICKS = 2;

	private final List<Candidate> waiting = new ArrayList<>();
	private final Map<ItemKey, Integer> handledAt = new HashMap<>();
	// A new collection log item is a first copy, so an earlier post of that name this login was that very copy
	private final Set<ItemKey> postedThisLogin = new HashSet<>();

	public void lootTrackerReported(Collection<String> itemNames, int tick)
	{
		for (String name : itemNames)
		{
			ItemKey key = ItemKey.of(name);
			waiting.removeIf(candidate -> candidate.isFor(key));
			handled(key, tick);
		}
	}

	public void containerGave(UntrackedLoot reward, int tick)
	{
		for (NamedItem item : reward.getItems())
		{
			ItemKey key = ItemKey.of(item.getName());
			if (handledJustNow(key, tick))
			{
				continue;
			}
			waiting.removeIf(candidate -> candidate.isFromCollectionLog() && candidate.isFor(key));
			waiting.add(new Candidate(reward.getSource(), item, key, tick, CONTAINER_WAIT_TICKS));
		}
	}

	public void collectionLogItem(NamedItem item, int tick)
	{
		ItemKey key = ItemKey.of(item.getName());
		if (alreadyCovered(key))
		{
			return;
		}
		waiting.add(new Candidate(COLLECTION_LOG_SOURCE, item, key, tick, COLLECTION_LOG_WAIT_TICKS));
	}

	// Strictly after the wait, so a Loot Tracker report on its last tick, which may come after the game tick, still wins
	public List<UntrackedLoot> due(int tick)
	{
		handledAt.values().removeIf(handled -> tick - handled > COLLECTION_LOG_WAIT_TICKS);
		List<Candidate> due = waiting.stream()
			.filter(candidate -> tick - candidate.getSince() > candidate.getWaitTicks())
			.collect(Collectors.toList());
		if (due.isEmpty())
		{
			return List.of();
		}
		waiting.removeAll(due);
		Map<String, List<NamedItem>> bySource = new LinkedHashMap<>();
		for (Candidate candidate : due)
		{
			handled(candidate.getKey(), tick);
			bySource.computeIfAbsent(candidate.getSource(), source -> new ArrayList<>()).add(candidate.getItem());
		}
		return bySource.entrySet().stream()
			.map(entry -> new UntrackedLoot(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	public void clear()
	{
		waiting.clear();
		handledAt.clear();
		postedThisLogin.clear();
	}

	private void handled(ItemKey key, int tick)
	{
		handledAt.put(key, tick);
		postedThisLogin.add(key);
	}

	private boolean alreadyCovered(ItemKey key)
	{
		return postedThisLogin.contains(key) || waiting.stream().anyMatch(candidate -> candidate.isFor(key));
	}

	private boolean handledJustNow(ItemKey key, int tick)
	{
		Integer handled = handledAt.get(key);
		return handled != null && tick - handled <= CONTAINER_WAIT_TICKS;
	}

	@Value
	private static class ItemKey
	{
		String normalizedName;

		static ItemKey of(String itemName)
		{
			return new ItemKey(Names.normalize(itemName));
		}
	}

	@Value
	private static class Candidate
	{
		String source;
		NamedItem item;
		ItemKey key;
		int since;
		int waitTicks;

		boolean isFromCollectionLog()
		{
			return COLLECTION_LOG_SOURCE.equals(source);
		}

		boolean isFor(ItemKey other)
		{
			return key.equals(other);
		}
	}
}
