package com.slayclan.task;

import com.slayclan.config.RemoteConfig;
import com.slayclan.config.TaskDefinition;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TaskMatcherTest
{
	private final TaskMatcher matcher = new TaskMatcher();

	@Test
	public void matchesTaskWithSameTriggerAndEnoughPlayers()
	{
		RemoteConfig config = configWith(task("cox", "RAID:Chambers of Xeric", 2));

		List<TaskDefinition> result = matcher.match(config, "RAID:Chambers of Xeric", 2);

		assertEquals(1, result.size());
		assertEquals("cox", result.get(0).getId());
	}

	@Test
	public void skipsTaskWhenTooFewPlayers()
	{
		RemoteConfig config = configWith(task("cox", "RAID:Chambers of Xeric", 2));

		assertTrue(matcher.match(config, "RAID:Chambers of Xeric", 1).isEmpty());
	}

	@Test
	public void skipsTaskWithOtherTrigger()
	{
		RemoteConfig config = configWith(task("tob", "TOB_COMPLETE", 1));

		assertTrue(matcher.match(config, "RAID:Chambers of Xeric", 3).isEmpty());
	}

	@Test
	public void matchesBossKillIgnoringCase()
	{
		RemoteConfig config = configWith(task("yama-duo", "KILL:yama", 2));

		assertEquals(1, matcher.match(config, "KILL:Yama", 2).size());
	}

	@Test
	public void skipsKillOfOtherBoss()
	{
		RemoteConfig config = configWith(task("yama-duo", "KILL:Yama", 2));

		assertTrue(matcher.match(config, "KILL:Zulrah", 2).isEmpty());
	}

	@Test
	public void findsTasksByTriggerRegardlessOfMinPlayers()
	{
		RemoteConfig config = configWith(task("drops", "DROP", 2), task("cox", "RAID:Chambers of Xeric", 1));

		List<TaskDefinition> result = matcher.byTrigger(config, "DROP");

		assertEquals(1, result.size());
		assertEquals("drops", result.get(0).getId());
	}

	@Test
	public void matchesWildcardTrigger()
	{
		RemoteConfig config = configWith(task("clan-raid", "RAID:*", 2));

		assertEquals(1, matcher.match(config, "RAID:Tombs of Amascut: Expert Mode", 2).size());
		assertTrue(matcher.match(config, "KILL:Yama", 2).isEmpty());
	}

	@Test
	public void matchesAnyAlternativeTrigger()
	{
		RemoteConfig config = configWith(task("backpack", "KILL:TzKal-Zuk | KILL:Sol Heredit", 1));

		assertEquals(1, matcher.match(config, "KILL:Sol Heredit", 1).size());
		assertEquals(1, matcher.match(config, "KILL:TzKal-Zuk", 1).size());
		assertTrue(matcher.match(config, "KILL:Zulrah", 1).isEmpty());
	}

	@Test
	public void returnsEveryMatchingTask()
	{
		RemoteConfig config = configWith(task("cox", "RAID:Chambers of Xeric", 2), task("cox-bonus", "RAID:Chambers of Xeric", 3));

		assertEquals(2, matcher.match(config, "RAID:Chambers of Xeric", 3).size());
	}

	@Test
	public void matchesTaskOnceWhenSeveralTriggersDescribeTheSameEvent()
	{
		RemoteConfig config = configWith(task("safe", "CA:Just To Be Safe|CA:#123", 1), task("by-id", "CA:#123", 1));

		List<TaskDefinition> result = matcher.match(config, List.of("CA:Just To Be Safe", "CA:#123"), 1);

		assertEquals(2, result.size());
		assertEquals("safe", result.get(0).getId());
	}

	private static RemoteConfig configWith(TaskDefinition... tasks)
	{
		RemoteConfig config = new RemoteConfig();
		config.setTasks(List.of(tasks));
		return config;
	}

	private static TaskDefinition task(String id, String trigger, int minPlayers)
	{
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		task.setTrigger(trigger);
		task.setMinPlayers(minPlayers);
		return task;
	}
}
