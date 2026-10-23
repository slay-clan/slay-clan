package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import com.slayclan.trigger.TimedLine;
import com.slayclan.trigger.TimedLine.Kind;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TimedTaskFilterTest
{
	private final TimedTaskFilter filter = new TimedTaskFilter();

	@Test
	public void acceptsTaskWithoutLimits()
	{
		assertTrue(filter.accepts(new TaskDefinition(), List.of(), 3, 0));
	}

	@Test
	public void acceptsTimeWithinLimit()
	{
		assertTrue(filter.accepts(task("1:00", null, 0), List.of(new TimedLine(Kind.SINGLE, 59)), 1, 0));
	}

	@Test
	public void acceptsTimeExactlyAtLimit()
	{
		assertTrue(filter.accepts(task("1:00", null, 0), List.of(new TimedLine(Kind.SINGLE, 60)), 1, 0));
	}

	@Test
	public void rejectsTimeOverLimit()
	{
		assertFalse(filter.accepts(task("1:00", null, 0), List.of(new TimedLine(Kind.SINGLE, 60.2)), 1, 0));
	}

	@Test
	public void rejectsWhenTimeIsMissing()
	{
		assertFalse(filter.accepts(task("1:00", null, 0), List.of(), 1, 0));
	}

	@Test
	public void usesTotalTimeByDefault()
	{
		List<TimedLine> tob = List.of(new TimedLine(Kind.SINGLE, 1002), new TimedLine(Kind.CHALLENGE, 1044.6), new TimedLine(Kind.TOTAL, 1207.8));

		assertFalse(filter.accepts(task("20:00", null, 0), tob, 3, 0));
	}

	@Test
	public void usesChallengeTimeWhenConfigured()
	{
		List<TimedLine> tob = List.of(new TimedLine(Kind.CHALLENGE, 1044.6), new TimedLine(Kind.TOTAL, 1207.8));

		assertTrue(filter.accepts(task("20:00", "challenge", 0), tob, 3, 0));
	}

	@Test
	public void fallsBackToSingleTimeWhenRaidHasOnlyOne()
	{
		assertTrue(filter.accepts(task("16:00", "challenge", 0), List.of(new TimedLine(Kind.SINGLE, 955)), 1, 0));
	}

	@Test
	public void requiresExactTeamSize()
	{
		assertTrue(filter.accepts(task(null, null, 3), List.of(), 3, 0));
		assertFalse(filter.accepts(task(null, null, 3), List.of(), 2, 0));
	}

	@Test
	public void rejectsUnreadableLimit()
	{
		assertFalse(filter.accepts(task("soon", null, 0), List.of(new TimedLine(Kind.SINGLE, 10)), 1, 0));
	}

	@Test
	public void requiresMinimumKillCount()
	{
		TaskDefinition task = new TaskDefinition();
		task.setMinKillCount(2);

		assertFalse(filter.accepts(task, List.of(), 1, 1));
		assertTrue(filter.accepts(task, List.of(), 1, 2));
	}

	private static TaskDefinition task(String maxTime, String timeType, int teamSize)
	{
		TaskDefinition task = new TaskDefinition();
		task.setMaxTime(maxTime);
		task.setTimeType(timeType);
		task.setTeamSize(teamSize);
		return task;
	}
}
