package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import java.util.List;

public class LootTaskEvaluator
{
	public boolean matches(TaskDefinition task, String source, String itemName)
	{
		List<String> items = task.getItems();
		List<String> sources = task.getSources();
		boolean sourceAllowed = sources == null || sources.isEmpty() || sources.stream().anyMatch(allowed -> Names.same(allowed, source));
		return sourceAllowed && items != null && items.stream().anyMatch(pattern -> Names.matches(pattern, itemName));
	}
}
