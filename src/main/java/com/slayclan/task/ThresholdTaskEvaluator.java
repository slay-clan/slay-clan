package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import com.slayclan.trigger.Triggers;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Skill;

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

	public static Optional<Skill> xpSkill(String trigger)
	{
		if (!trigger.toUpperCase().startsWith(Triggers.XP_PREFIX))
		{
			return Optional.empty();
		}
		String skillName = trigger.substring(Triggers.XP_PREFIX.length());
		return Arrays.stream(Skill.values())
			.filter(skill -> Names.same(skill.getName(), skillName))
			.findFirst();
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
