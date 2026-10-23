package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ClogTaskEvaluatorTest
{
	private final ClogTaskEvaluator evaluator = new ClogTaskEvaluator();

	@Test
	public void ignoresItemOutsideTask()
	{
		assertFalse(evaluator.progressAfter(task(0, "Fire cape"), Set.of(), "Ancestral robe top").isPresent());
	}

	@Test
	public void completesAnyOfTaskWithSingleItem()
	{
		TaskDefinition task = task(0, "Craw's bow (u)", "Thammaron's sceptre (u)", "Viggora's chainmace (u)");

		Set<String> progress = evaluator.progressAfter(task, Set.of(), "Thammaron's sceptre (u)").orElseThrow();

		assertTrue(evaluator.isComplete(task, progress));
	}

	@Test
	public void keepsPartialProgressUntilRequiredCountReached()
	{
		TaskDefinition task = task(2, "Sanguine dust", "Metamorphic dust");

		Set<String> first = evaluator.progressAfter(task, Set.of(), "Sanguine dust").orElseThrow();
		Set<String> second = evaluator.progressAfter(task, first, "Metamorphic dust").orElseThrow();

		assertFalse(evaluator.isComplete(task, first));
		assertTrue(evaluator.isComplete(task, second));
	}

	@Test
	public void countsSameItemOnlyOnce()
	{
		TaskDefinition task = task(2, "Evil chicken head", "Evil chicken wings", "Evil chicken legs", "Evil chicken feet");

		Set<String> first = evaluator.progressAfter(task, Set.of(), "Evil chicken head").orElseThrow();
		Set<String> again = evaluator.progressAfter(task, first, "Evil chicken head").orElseThrow();

		assertEquals(1, again.size());
		assertFalse(evaluator.isComplete(task, again));
	}

	@Test
	public void matchesItemIgnoringCaseAndStoresConfiguredName()
	{
		TaskDefinition task = task(0, "Pet penance queen");

		Optional<Set<String>> progress = evaluator.progressAfter(task, Set.of(), "Pet Penance Queen");

		assertEquals(Set.of("Pet penance queen"), progress.orElseThrow());
	}

	@Test
	public void matchesWildcardItem()
	{
		TaskDefinition task = task(0, "3rd age*");

		assertEquals(Set.of("3rd age*"), evaluator.progressAfter(task, Set.of(), "3rd age platebody").orElseThrow());
	}

	@Test
	public void treatsMissingItemListAsNoMatch()
	{
		TaskDefinition task = new TaskDefinition();

		assertFalse(evaluator.progressAfter(task, Set.of(), "Fire cape").isPresent());
	}

	private static TaskDefinition task(int required, String... items)
	{
		TaskDefinition task = new TaskDefinition();
		task.setId("task");
		task.setTrigger("CLOG");
		task.setItems(List.of(items));
		task.setRequired(required);
		return task;
	}
}
