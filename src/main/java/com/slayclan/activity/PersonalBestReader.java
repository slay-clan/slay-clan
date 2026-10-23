package com.slayclan.activity;

import com.slayclan.trigger.TimeFormat;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the personal bests RuneLite's Chat Commands plugin keeps per account, so the clan board can be filled
 * at login instead of waiting for the next kill of every boss.
 */
public class PersonalBestReader
{
	static final String GROUP = "personalbest";

	private final ConfigManager configManager;

	public PersonalBestReader(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public List<StoredPersonalBest> read()
	{
		List<StoredPersonalBest> bests = new ArrayList<>();
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return bests;
		}
		for (String boss : configManager.getRSProfileConfigurationKeys(GROUP, profile, ""))
		{
			Double seconds = configManager.getRSProfileConfiguration(GROUP, boss, double.class);
			if (seconds != null && seconds > 0)
			{
				bests.add(new StoredPersonalBest(boss, seconds, TimeFormat.of(seconds)));
			}
		}
		return bests;
	}
}
