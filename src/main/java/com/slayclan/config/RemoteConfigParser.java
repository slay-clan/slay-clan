package com.slayclan.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the clan config. "tasks" is either a list of tasks or an object whose keys are tier names and whose
 * values are lists of tasks. In the grouped form a task without its own "tier" gets the group's name.
 */
@Slf4j
public class RemoteConfigParser
{
	private final Gson gson;

	public RemoteConfigParser(Gson gson)
	{
		this.gson = gson;
	}

	public Optional<RemoteConfig> parse(String json)
	{
		RemoteConfig config;
		try
		{
			config = toConfig(gson.fromJson(json, JsonObject.class));
		}
		catch (JsonParseException | IllegalStateException | UnsupportedOperationException e)
		{
			return Optional.empty();
		}

		return isUsable(config) ? Optional.of(config) : Optional.empty();
	}

	private static boolean isUsable(RemoteConfig config)
	{
		return config != null && hasRequiredFields(config) && hasSecretSafeApiUrl(config) && hasUniqueIds(config.getTasks());
	}

	private static boolean hasRequiredFields(RemoteConfig config)
	{
		return !isBlank(config.getClanName()) && config.getTasks() != null;
	}

	// Posts carry the member's token
	private static boolean hasSecretSafeApiUrl(RemoteConfig config)
	{
		if (SecretSafeUrl.accepts(config.getApiUrl()))
		{
			return true;
		}
		log.info("Clan config rejected: apiUrl must be https, or http on this computer");
		return false;
	}

	/**
	 * One-time memory is keyed by id, so a missing or repeated id would make unrelated tasks share it. RuneLite
	 * refuses config keys containing ':', so such an id could never be remembered.
	 */
	private static boolean hasUniqueIds(List<TaskDefinition> tasks)
	{
		Set<String> seen = new HashSet<>();
		for (TaskDefinition task : tasks)
		{
			if (isBlank(task.getId()))
			{
				log.info("Clan config rejected: task '{}' has no id", task.getName());
				return false;
			}
			if (task.getId().contains(":"))
			{
				log.info("Clan config rejected: task id '{}' contains ':'", task.getId());
				return false;
			}
			if (!seen.add(task.getId().trim()))
			{
				log.info("Clan config rejected: task id '{}' is used more than once", task.getId());
				return false;
			}
		}
		return true;
	}

	private RemoteConfig toConfig(JsonObject root)
	{
		if (root == null)
		{
			return null;
		}
		RemoteConfig config = new RemoteConfig();
		config.setClanName(string(root, "clanName"));
		config.setApiUrl(string(root, "apiUrl"));
		config.setTasks(tasks(root.get("tasks")));
		config.setIgnoredContent(strings(root.get("ignoredContent")));
		return config;
	}

	/**
	 * The switch-off list is edited in a hurry, so a single name written without brackets counts as a list of one
	 * instead of rejecting the whole config and silencing every post.
	 */
	private List<String> strings(JsonElement element)
	{
		if (element == null || element.isJsonNull())
		{
			return List.of();
		}
		if (element.isJsonPrimitive())
		{
			return List.of(element.getAsString());
		}
		return Arrays.asList(gson.fromJson(element, String[].class));
	}

	private List<TaskDefinition> tasks(JsonElement element)
	{
		if (element == null || element.isJsonNull())
		{
			return null;
		}
		if (element.isJsonArray())
		{
			return new ArrayList<>(Arrays.asList(gson.fromJson(element, TaskDefinition[].class)));
		}
		List<TaskDefinition> tasks = new ArrayList<>();
		for (Map.Entry<String, JsonElement> group : element.getAsJsonObject().entrySet())
		{
			for (TaskDefinition task : gson.fromJson(group.getValue().getAsJsonArray(), TaskDefinition[].class))
			{
				if (task.getTier() == null)
				{
					task.setTier(group.getKey());
				}
				tasks.add(task);
			}
		}
		return tasks;
	}

	private static String string(JsonObject root, String key)
	{
		JsonElement value = root.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.trim().isEmpty();
	}
}
