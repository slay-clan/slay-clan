package com.slayclan.drop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.api.Item;

/**
 * Only an opened reward container makes new inventory items count, since banks, trades, shops and pickups would
 * otherwise post as drops.
 */
public class RewardContainerWatch
{
	// Covers running about ten tiles to the container plus its opening animation
	static final int WATCH_TICKS = 10;

	private String container;
	private int clickedAt;
	private Map<Integer, Integer> before = Map.of();

	public void clicked(String container, Item[] inventory, int tick)
	{
		this.container = container;
		clickedAt = tick;
		before = counts(inventory);
	}

	public void clickedSomethingElse()
	{
		container = null;
	}

	public Optional<ContainerReward> inventoryChanged(Item[] inventory, int tick)
	{
		if (!watching(tick))
		{
			container = null;
			return Optional.empty();
		}
		Map<Integer, Integer> after = counts(inventory);
		List<Item> gained = after.entrySet().stream()
			.filter(entry -> entry.getValue() > before.getOrDefault(entry.getKey(), 0))
			.map(entry -> new Item(entry.getKey(), entry.getValue() - before.getOrDefault(entry.getKey(), 0)))
			.collect(Collectors.toList());
		before = after;
		return gained.isEmpty() ? Optional.empty() : Optional.of(new ContainerReward(container, gained));
	}

	private boolean watching(int tick)
	{
		return container != null && tick - clickedAt <= WATCH_TICKS;
	}

	public void clear()
	{
		container = null;
		before = Map.of();
	}

	private static Map<Integer, Integer> counts(Item[] inventory)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (Item item : inventory)
		{
			if (item.getId() >= 0)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}
}
