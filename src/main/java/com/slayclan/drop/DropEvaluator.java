package com.slayclan.drop;

import com.slayclan.config.TaskDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DropEvaluator
{
	/**
	 * The drops to post, plus the items worth at least the task's minimum value that the task leaves out, each with
	 * the reason.
	 */
	public DropJudgement evaluate(List<PricedItem> items, TaskDefinition dropTask)
	{
		long minValue = Math.max(1, dropTask.getMinValue());
		List<ValuableDrop> drops = new ArrayList<>();
		List<SkippedDrop> skipped = new ArrayList<>();
		for (PricedItem item : items)
		{
			Optional<String> reasonNotCounted = reasonNotCounted(item, dropTask);
			toDrops(item)
				.filter(drop -> judgedValue(item, drop, dropTask) >= minValue)
				.forEach(drop -> reasonNotCounted.ifPresentOrElse(
					reason -> skipped.add(new SkippedDrop(drop.getItemName(), drop.getValue(), reason)),
					() -> drops.add(withPoints(drop, dropTask.getValuePerPoint()))));
		}
		return new DropJudgement(drops, skipped);
	}

	private static Optional<String> reasonNotCounted(PricedItem item, TaskDefinition dropTask)
	{
		if (excludedAsStackable(item, dropTask))
		{
			return Optional.of("stackable items do not count");
		}
		if (dropTask.getValuePerPoint() <= 0)
		{
			return Optional.of("the DROP task sets no valuePerPoint");
		}
		return Optional.empty();
	}

	// Runes and coins from a raid chest add up past the minimum as a stack, so a left-out stack is only worth
	// mentioning when a single unit reaches it
	private static long judgedValue(PricedItem item, ValuableDrop drop, TaskDefinition dropTask)
	{
		return excludedAsStackable(item, dropTask) ? item.getUnitPrice() : drop.getValue();
	}

	private static boolean excludedAsStackable(PricedItem item, TaskDefinition dropTask)
	{
		return item.isStackable() && !dropTask.isIncludeStackable();
	}

	private static ValuableDrop withPoints(ValuableDrop drop, long valuePerPoint)
	{
		return new ValuableDrop(drop.getItemName(), drop.getQuantity(), drop.getValue(), roundedPoints(drop.getValue(), valuePerPoint));
	}

	/**
	 * Rounds half up, the way the clan rounds drop values: 4.4M is 4 points, 4.5M and 4.8M are 5.
	 */
	private static long roundedPoints(long value, long valuePerPoint)
	{
		return (value * 2 + valuePerPoint) / (valuePerPoint * 2);
	}

	private static Stream<ValuableDrop> toDrops(PricedItem item)
	{
		if (item.isStackable())
		{
			return Stream.of(new ValuableDrop(item.getName(), item.getQuantity(), item.getUnitPrice() * item.getQuantity(), 0));
		}
		return IntStream.range(0, item.getQuantity())
			.mapToObj(copy -> new ValuableDrop(item.getName(), 1, item.getUnitPrice(), 0));
	}
}
