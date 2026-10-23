package com.slayclan.progress;

import com.slayclan.SlayClanConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Memory of posted one-time tasks and partial collection log progress. It lives in the RuneLite profile, so a
 * main and its alts on the same profile share it, as the clan counts them as one player.
 */
@Singleton
public class TaskProgressStore
{
	private static final String COMPLETED_PREFIX = "completed.";
	private static final String CLOG_PREFIX = "clog.";
	private static final String BASELINE_PREFIX = "baseline.";
	private static final String ITEM_SEPARATOR = "|";

	private final ConfigManager configManager;

	@Inject
	public TaskProgressStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public boolean isCompleted(String taskId)
	{
		return "true".equals(configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, COMPLETED_PREFIX + taskId));
	}

	public void markCompleted(String taskId)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, COMPLETED_PREFIX + taskId, "true");
	}

	public Set<String> loggedItems(String taskId)
	{
		String stored = configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, CLOG_PREFIX + taskId);
		if (stored == null || stored.isEmpty())
		{
			return Collections.emptySet();
		}
		return Arrays.stream(stored.split("\\" + ITEM_SEPARATOR)).collect(Collectors.toSet());
	}

	public void saveLoggedItems(String taskId, Set<String> items)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, CLOG_PREFIX + taskId,
			String.join(ITEM_SEPARATOR, new TreeSet<>(items)));
	}

	/**
	 * A task is baselined once the plugin has recorded whether its condition was already met for this account,
	 * so that only conditions met afterwards get posted. Completion is shared across the profile, the baseline
	 * is not, as an alt that lacks an item says nothing about the main.
	 */
	public boolean isBaselined(String taskId, long accountHash)
	{
		return "true".equals(configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, baselineKey(taskId, accountHash)));
	}

	public void markBaselined(String taskId, long accountHash)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, baselineKey(taskId, accountHash), "true");
	}

	private static String baselineKey(String taskId, long accountHash)
	{
		return BASELINE_PREFIX + taskId + "." + accountHash;
	}
}
