package com.slayclan.drop;

import com.slayclan.config.TaskDefinition;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DropEvaluator
{
	public List<ValuableDrop> evaluate(List<PricedItem> items, TaskDefinition dropTask)
	{
		long valuePerPoint = dropTask.getValuePerPoint();
		if (valuePerPoint <= 0)
		{
			return Collections.emptyList();
		}
		long minValue = Math.max(1, dropTask.getMinValue());

		return items.stream()
			.filter(item -> dropTask.isIncludeStackable() || !item.isStackable())
			.flatMap(DropEvaluator::toDrops)
			.filter(drop -> drop.getValue() >= minValue)
			.map(drop -> new ValuableDrop(drop.getItemName(), drop.getQuantity(), drop.getValue(), roundedPoints(drop.getValue(), valuePerPoint)))
			.collect(Collectors.toList());
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
