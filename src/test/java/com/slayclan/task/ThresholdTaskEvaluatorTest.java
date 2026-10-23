package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ThresholdTaskEvaluatorTest
{
	private final ThresholdTaskEvaluator evaluator = new ThresholdTaskEvaluator();

	@Test
	public void wearingAnyListedItemSatisfiesSingleItemTask()
	{
		TaskDefinition task = equip(0, "Quest point cape", "Quest point cape (t)");

		assertTrue(evaluator.isEquipped(task, Set.of("Quest point cape (t)", "Dragon boots")));
	}

	@Test
	public void requiresEveryPieceWhenRequiredMatchesSet()
	{
		TaskDefinition task = equip(3, "Sanguine torva full helm", "Sanguine torva platebody", "Sanguine torva platelegs");

		assertFalse(evaluator.isEquipped(task, Set.of("Sanguine torva full helm", "Sanguine torva platebody", "Torva platelegs")));
		assertTrue(evaluator.isEquipped(task, Set.of("Sanguine torva full helm", "Sanguine torva platebody", "Sanguine torva platelegs")));
	}

	@Test
	public void matchesEquipmentIgnoringCaseAndApostrophes()
	{
		assertTrue(evaluator.isEquipped(equip(0, "Ava's assembler"), Set.of("avas assembler")));
	}

	@Test
	public void rejectsEquipTaskWithoutItems()
	{
		assertFalse(evaluator.isEquipped(new TaskDefinition(), Set.of("Quest point cape")));
	}

	@Test
	public void reachesExperienceThresholdAtExactValue()
	{
		TaskDefinition task = new TaskDefinition();
		task.setMinXp(20_000_000);

		assertTrue(evaluator.hasExperience(task, 20_000_000));
		assertFalse(evaluator.hasExperience(task, 19_999_999));
	}

	@Test
	public void reachesCollectionLogCountAtExactValue()
	{
		TaskDefinition task = new TaskDefinition();
		task.setMinCount(400);

		assertTrue(evaluator.hasCount(task, 400));
		assertFalse(evaluator.hasCount(task, 399));
	}

	private static TaskDefinition equip(int required, String... items)
	{
		TaskDefinition task = new TaskDefinition();
		task.setTrigger("EQUIP");
		task.setItems(List.of(items));
		task.setRequired(required);
		return task;
	}
}
