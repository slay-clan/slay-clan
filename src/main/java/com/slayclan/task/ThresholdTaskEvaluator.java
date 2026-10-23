package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import java.util.List;
import java.util.Set;

public class ThresholdTaskEvaluator
{
	public boolean isEquipped(TaskDefinition task, Set<String> wornItemNames)
	{
		List<String> items = task.getItems();
		if (items == null || items.isEmpty())
		{
			return false;
		}
		long wornMatches = items.stream()
			.filter(pattern -> wornItemNames.stream().anyMatch(worn -> Names.matches(pattern, worn)))
			.count();
		return wornMatches >= Math.max(1, task.getRequired());
	}

	public boolean hasExperience(TaskDefinition task, long experience)
	{
		return experience >= task.getMinXp();
	}

	public boolean hasCount(TaskDefinition task, int count)
	{
		return count >= task.getMinCount();
	}
}
