package com.slayclan.activity;

import com.slayclan.SlayClanConfig;
import com.slayclan.trigger.TimedLine;
import java.util.Locale;
import net.runelite.client.config.ConfigManager;

/**
 * The plugin's own fastest kill per boss, time type and team size, kept per account in the RuneLite profile. The
 * game only remembers one personal best per boss, so a solo time slower than the duo time is never called a record
 * by the game; this memory lets the plugin report it anyway. Also remembers which bosses have had their game PB
 * reported, so that happens once per account rather than once per login. A PB season in the clan config starts the
 * fastest-kill memory over, so the first kill of a season is reported even when an earlier season was faster.
 */
public class ScaleBestStore
{
	private static final String BEST_PREFIX = "scalebest.";
	private static final String REPORTED_PREFIX = "gamebestreported.";

	private final ConfigManager configManager;
	private final String season;

	public ScaleBestStore(ConfigManager configManager)
	{
		this(configManager, "");
	}

	private ScaleBestStore(ConfigManager configManager, String season)
	{
		this.configManager = configManager;
		this.season = season;
	}

	/** Any text is a season and a different text is a different season; empty means every kill since install counts. */
	public ScaleBestStore forSeason(String season)
	{
		return new ScaleBestStore(configManager, season == null ? "" : season.trim());
	}

	public boolean isFasterThanStored(long accountHash, String boss, TimedLine.Kind kind, int teamSize, double seconds)
	{
		String stored = configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, bestKey(accountHash, boss, kind, teamSize));
		return stored == null || seconds < storedSeconds(stored);
	}

	// An unreadable value is treated as no memory, so a corrupt profile entry cannot stop every kill at that boss
	private static double storedSeconds(String stored)
	{
		try
		{
			return Double.parseDouble(stored);
		}
		catch (NumberFormatException e)
		{
			return Double.MAX_VALUE;
		}
	}

	public void saveBest(long accountHash, String boss, TimedLine.Kind kind, int teamSize, double seconds)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, bestKey(accountHash, boss, kind, teamSize), String.valueOf(seconds));
	}

	public boolean isGameBestReported(long accountHash, String boss)
	{
		return "true".equals(configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, reportedKey(accountHash, boss)));
	}

	public void markGameBestReported(long accountHash, String boss)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, reportedKey(accountHash, boss), "true");
	}

	private String bestKey(long accountHash, String boss, TimedLine.Kind kind, int teamSize)
	{
		return BEST_PREFIX + seasonPrefix() + accountHash + "." + slug(boss) + "." + kind.name().toLowerCase(Locale.ROOT) + "." + teamSize;
	}

	private String seasonPrefix()
	{
		return season.isEmpty() ? "" : slug(season) + ".";
	}

	private static String reportedKey(long accountHash, String boss)
	{
		return REPORTED_PREFIX + accountHash + "." + slug(boss);
	}

	private static String slug(String text)
	{
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}
}
