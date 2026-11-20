package com.slayclan.activity;

import com.slayclan.SlayClanConfig;
import com.slayclan.trigger.TimeFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigManager;

/**
 * The game's total time record in the Tombs of Amascut, per account, mode and team size. RuneLite only stores the
 * challenge time there, while the clan ranks the total, so the plugin keeps the record printed on the total line
 * after each raid and sends it in the snapshot instead of RuneLite's ToA bests. Entries are keyed like RuneLite's,
 * such as "tombs of amascut expert mode 3 players total".
 */
public class ToaTotalBestStore
{
	private static final String PREFIX = "toatotal.";
	private static final String TOMBS_OF_AMASCUT = "tombs of amascut";
	private static final List<String> MODES = List.of(TOMBS_OF_AMASCUT, "tombs of amascut expert mode", "tombs of amascut entry mode");
	private static final int MAX_TEAM_SIZE = 8;

	private final ConfigManager configManager;

	public ToaTotalBestStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The raid is named as in its completion message, such as "Tombs of Amascut: Expert Mode". */
	public void rememberIfFaster(long accountHash, String raid, int teamSize, double seconds)
	{
		String mode = raid.toLowerCase(Locale.ROOT).replace(":", "");
		if (!isToaScale(mode, teamSize))
		{
			return;
		}
		String key = storageKey(accountHash, snapshotKey(mode, teamSize));
		if (seconds < storedSeconds(key))
		{
			configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, key, String.valueOf(seconds));
		}
	}

	private static boolean isToaScale(String mode, int teamSize)
	{
		return MODES.contains(mode) && teamSize >= 1 && teamSize <= MAX_TEAM_SIZE;
	}

	public List<StoredPersonalBest> withTotalsInsteadOfChallengeTimes(long accountHash, List<StoredPersonalBest> runeLiteBests)
	{
		List<StoredPersonalBest> bests = runeLiteBests.stream()
			.filter(best -> !best.getBoss().startsWith(TOMBS_OF_AMASCUT))
			.collect(Collectors.toCollection(ArrayList::new));
		bests.addAll(totals(accountHash));
		return bests;
	}

	private List<StoredPersonalBest> totals(long accountHash)
	{
		List<StoredPersonalBest> totals = new ArrayList<>();
		for (String mode : MODES)
		{
			for (int teamSize = 1; teamSize <= MAX_TEAM_SIZE; teamSize++)
			{
				String snapshotKey = snapshotKey(mode, teamSize);
				remembered(storageKey(accountHash, snapshotKey))
					.ifPresent(seconds -> totals.add(new StoredPersonalBest(snapshotKey, seconds, TimeFormat.of(seconds))));
			}
		}
		return totals;
	}

	private double storedSeconds(String key)
	{
		return remembered(key).orElse(Double.MAX_VALUE);
	}

	private Optional<Double> remembered(String key)
	{
		String stored = configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, key);
		return stored == null ? Optional.empty() : ScaleBestStore.parsedSeconds(stored);
	}

	private static String snapshotKey(String mode, int teamSize)
	{
		return mode + " " + (teamSize == 1 ? "solo" : teamSize + " players") + " total";
	}

	private static String storageKey(long accountHash, String snapshotKey)
	{
		return PREFIX + accountHash + "." + snapshotKey.replace(' ', '-');
	}
}
