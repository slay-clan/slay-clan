package com.slayclan.drop;

import com.slayclan.config.TaskDefinition;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DropEvaluatorTest
{
	private final DropEvaluator evaluator = new DropEvaluator();

	@Test
	public void awardsPointsRoundedToNearestStep()
	{
		List<ValuableDrop> drops = evaluator.evaluate(List.of(single("Dragon warhammer", 452_312)), dropTask());

		assertEquals(1, drops.size());
		assertEquals("Dragon warhammer", drops.get(0).getItemName());
		assertEquals(452_312, drops.get(0).getValue());
		assertEquals(45, drops.get(0).getPoints());
	}

	@Test
	public void roundsMillionsLikeTheClan()
	{
		TaskDefinition task = dropTask();
		task.setMinValue(1_000_000);
		task.setValuePerPoint(1_000_000);

		List<ValuableDrop> drops = evaluator.evaluate(List.of(
			single("Four point four", 4_400_000),
			single("Four point five", 4_500_000),
			single("Four point eight", 4_800_000)), task);

		assertEquals(4, drops.get(0).getPoints());
		assertEquals(5, drops.get(1).getPoints());
		assertEquals(5, drops.get(2).getPoints());
	}

	@Test
	public void includesDropExactlyAtMinimumValue()
	{
		List<ValuableDrop> drops = evaluator.evaluate(List.of(single("Rune platebody", 10_000)), dropTask());

		assertEquals(1, drops.get(0).getPoints());
	}

	@Test
	public void skipsDropBelowMinimumValue()
	{
		assertTrue(evaluator.evaluate(List.of(single("Rune med helm", 9_999)), dropTask()).isEmpty());
	}

	@Test
	public void skipsStackableItemsRegardlessOfValue()
	{
		List<PricedItem> items = List.of(
			new PricedItem("Coins", 500_000, 1, true),
			new PricedItem("Dragon bones", 50, 2_500, true),
			new PricedItem("Zulrah's scales", 1, 50_000, true));

		assertTrue(evaluator.evaluate(items, dropTask()).isEmpty());
	}

	@Test
	public void countsWholeStackWhenStackablesAreIncluded()
	{
		TaskDefinition task = dropTask();
		task.setIncludeStackable(true);

		List<ValuableDrop> drops = evaluator.evaluate(List.of(new PricedItem("Zulrah's scales", 500, 150, true)), task);

		assertEquals(1, drops.size());
		assertEquals(500, drops.get(0).getQuantity());
		assertEquals(75_000, drops.get(0).getValue());
		assertEquals(8, drops.get(0).getPoints());
	}

	@Test
	public void skipsIncludedStackBelowMinimumValue()
	{
		TaskDefinition task = dropTask();
		task.setIncludeStackable(true);

		assertTrue(evaluator.evaluate(List.of(new PricedItem("Coins", 9_999, 1, true)), task).isEmpty());
	}

	@Test
	public void handlesStackValuesBeyondIntRange()
	{
		TaskDefinition task = dropTask();
		task.setIncludeStackable(true);

		List<ValuableDrop> drops = evaluator.evaluate(List.of(new PricedItem("Coins", 2_000_000_000, 2, true)), task);

		assertEquals(4_000_000_000L, drops.get(0).getValue());
	}

	@Test
	public void countsEachUnstackableCopySeparately()
	{
		List<ValuableDrop> drops = evaluator.evaluate(List.of(new PricedItem("Rune platebody", 2, 38_000, false)), dropTask());

		assertEquals(2, drops.size());
		assertEquals(38_000, drops.get(0).getValue());
		assertEquals(4, drops.get(1).getPoints());
	}

	@Test
	public void evaluatesEachItemSeparately()
	{
		List<ValuableDrop> drops = evaluator.evaluate(List.of(
			single("Dragon warhammer", 452_312),
			single("Bones", 100),
			single("Rune platebody", 38_000)), dropTask());

		assertEquals(2, drops.size());
		assertEquals(4, drops.get(1).getPoints());
	}

	@Test
	public void skipsUntradeableItemsWorthNothing()
	{
		assertTrue(evaluator.evaluate(List.of(single("Clue scroll (elite)", 0)), dropTask()).isEmpty());
	}

	@Test
	public void ignoresTaskWithoutValuePerPoint()
	{
		TaskDefinition task = dropTask();
		task.setValuePerPoint(0);

		assertTrue(evaluator.evaluate(List.of(single("Dragon warhammer", 452_312)), task).isEmpty());
	}

	@Test
	public void handlesPricesBeyondIntRange()
	{
		List<ValuableDrop> drops = evaluator.evaluate(List.of(single("Twisted bow", 3_000_000_000L)), dropTask());

		assertEquals(300_000, drops.get(0).getPoints());
	}

	private static PricedItem single(String name, long price)
	{
		return new PricedItem(name, 1, price, false);
	}

	private static TaskDefinition dropTask()
	{
		TaskDefinition task = new TaskDefinition();
		task.setId("drops");
		task.setName("Valuable drop");
		task.setTrigger("DROP");
		task.setTier("Repeatable");
		task.setMinValue(10_000);
		task.setValuePerPoint(10_000);
		return task;
	}
}
