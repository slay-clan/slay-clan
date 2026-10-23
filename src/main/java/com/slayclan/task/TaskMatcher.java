package com.slayclan.task;

import com.slayclan.config.RemoteConfig;
import com.slayclan.config.TaskDefinition;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class TaskMatcher
{
	private static final String TRIGGER_ALTERNATIVE_SEPARATOR = "\\|";

	public List<TaskDefinition> match(RemoteConfig config, String trigger, int participantCount)
	{
		return match(config, List.of(trigger), participantCount);
	}

	/**
	 * Tasks matching any of several triggers that describe the same event, each task at most once.
	 */
	public List<TaskDefinition> match(RemoteConfig config, Collection<String> triggers, int participantCount)
	{
		return config.getTasks().stream()
			.filter(task -> triggers.stream().anyMatch(trigger -> matchesTrigger(task.getTrigger(), trigger)))
			.filter(task -> participantCount >= task.getMinPlayers())
			.collect(Collectors.toList());
	}

	public List<TaskDefinition> byTrigger(RemoteConfig config, String trigger)
	{
		return config.getTasks().stream()
			.filter(task -> matchesTrigger(task.getTrigger(), trigger))
			.collect(Collectors.toList());
	}

	private static boolean matchesTrigger(String taskTrigger, String trigger)
	{
		return taskTrigger != null && Arrays.stream(taskTrigger.split(TRIGGER_ALTERNATIVE_SEPARATOR))
			.anyMatch(alternative -> Names.matches(alternative.trim(), trigger));
	}
}
