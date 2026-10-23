package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import com.slayclan.trigger.TimeParser;
import com.slayclan.trigger.TimedLine;
import com.slayclan.trigger.TimedLine.Kind;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public class TimedTaskFilter
{
	private static final String CHALLENGE_TIME_TYPE = "challenge";

	public boolean accepts(TaskDefinition task, List<TimedLine> times, int teamSize, int killCount)
	{
		if ((task.getTeamSize() > 0 && task.getTeamSize() != teamSize) || killCount < task.getMinKillCount())
		{
			return false;
		}
		String maxTime = task.getMaxTime();
		if (maxTime == null || maxTime.trim().isEmpty())
		{
			return true;
		}
		OptionalDouble limit = TimeParser.parseSeconds(maxTime);
		Optional<TimedLine> time = pick(times, CHALLENGE_TIME_TYPE.equalsIgnoreCase(task.getTimeType()) ? Kind.CHALLENGE : Kind.TOTAL);
		return limit.isPresent() && time.isPresent() && time.get().getSeconds() <= limit.getAsDouble();
	}

	private static Optional<TimedLine> pick(List<TimedLine> times, Kind preferred)
	{
		return times.stream().filter(line -> line.getKind() == preferred).findFirst()
			.or(() -> times.stream().filter(line -> line.getKind() == Kind.SINGLE).findFirst());
	}
}
