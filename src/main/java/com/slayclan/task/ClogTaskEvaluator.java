package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ClogTaskEvaluator
{
	/**
	 * Returns the task's progress including {@code loggedItem}, using the item names as written in the task,
	 * or empty when the item is not part of the task.
	 */
	public Optional<Set<String>> progressAfter(TaskDefinition task, Set<String> currentProgress, String loggedItem)
	{
		List<String> items = task.getItems();
		if (items == null)
		{
			return Optional.empty();
		}
		return items.stream()
			.filter(item -> Names.matches(item, loggedItem))
			.findFirst()
			.map(item ->
			{
				Set<String> progress = new HashSet<>(currentProgress);
				progress.add(item);
				return progress;
			});
	}

	/**
	 * The listed items among a whole collection log, as opposed to one newly logged item.
	 */
	public Set<String> ownedOf(TaskDefinition task, Collection<String> ownedItems)
	{
		List<String> items = task.getItems();
		if (items == null)
		{
			return Set.of();
		}
		return items.stream()
			.filter(item -> ownedItems.stream().anyMatch(owned -> Names.matches(item, owned)))
			.collect(Collectors.toSet());
	}

	public boolean isComplete(TaskDefinition task, Set<String> progress)
	{
		return progress.size() >= Math.max(1, task.getRequired());
	}
}
