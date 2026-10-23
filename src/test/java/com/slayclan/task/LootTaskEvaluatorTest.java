package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LootTaskEvaluatorTest
{
	private final LootTaskEvaluator evaluator = new LootTaskEvaluator();

	@Test
	public void matchesItemByPrefixPattern()
	{
		assertTrue(evaluator.matches(task(List.of("Jar of*"), null), "Kalphite Queen", "Jar of sand"));
	}

	@Test
	public void matchesExactItemIgnoringApostrophesAndCase()
	{
		assertTrue(evaluator.matches(task(List.of("Twisted ancestral colour kit"), null), "Chambers of Xeric", "twisted ancestral colour kit"));
	}

	@Test
	public void rejectsItemOutsideTask()
	{
		assertFalse(evaluator.matches(task(List.of("Metamorphic dust"), null), "Chambers of Xeric", "Dexterous prayer scroll"));
	}

	@Test
	public void requiresListedSourceWhenSourcesAreGiven()
	{
		TaskDefinition task = task(List.of("Metamorphic dust", "Sanguine dust"), List.of("Chambers of Xeric", "Theatre of Blood"));

		assertTrue(evaluator.matches(task, "Theatre of Blood", "Sanguine dust"));
		assertFalse(evaluator.matches(task, "Simulated drop", "Sanguine dust"));
	}

	@Test
	public void prefixPatternDoesNotMatchShorterName()
	{
		assertFalse(evaluator.matches(task(List.of("Jar of*"), null), "Kraken", "Jar"));
	}

	@Test
	public void rejectsTaskWithoutItems()
	{
		assertFalse(evaluator.matches(task(null, null), "Zulrah", "Jar of swamp"));
	}

	private static TaskDefinition task(List<String> items, List<String> sources)
	{
		TaskDefinition task = new TaskDefinition();
		task.setId("loot");
		task.setTrigger("LOOT");
		task.setItems(items);
		task.setSources(sources);
		return task;
	}
}
