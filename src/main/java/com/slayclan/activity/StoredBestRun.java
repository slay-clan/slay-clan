package com.slayclan.activity;

import com.slayclan.trigger.TimedLine;
import com.slayclan.trigger.TimedLine.Kind;
import com.slayclan.trigger.Triggers;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A stored personal best read as the run that set it. RuneLite keeps the key in lowercase with the raid mode's
 * colon dropped, such as "tombs of amascut expert mode 3 players", and keeps the time the game prints without a
 * "total" prefix: the room time in the Theatre of Blood and the Tombs of Amascut, the one duration in the
 * Chambers of Xeric and the total time in the Fractured Archive. A boss best says nothing about the team size.
 */
@Value
public class StoredBestRun
{
	private static final Pattern TEAM_SUFFIX = Pattern.compile("^(.*?) (?:(\\d+) players|solo)$");
	private static final Pattern RANGE_SUFFIX = Pattern.compile("^(.*?) \\d+(?:-\\d+|\\+) players$");
	private static final Map<String, Kind> RAID_TIME_KINDS = Map.of(
		"chambers of xeric", Kind.SINGLE,
		"theatre of blood", Kind.CHALLENGE,
		"tombs of amascut", Kind.CHALLENGE,
		"fractured archive", Kind.TOTAL);
	private static final Map<String, String> GAME_NAMES = Map.of(
		"tombs of amascut expert mode", "Tombs of Amascut: Expert Mode",
		"tombs of amascut entry mode", "Tombs of Amascut: Entry Mode",
		"theatre of blood hard mode", "Theatre of Blood: Hard Mode",
		"theatre of blood entry mode", "Theatre of Blood: Entry Mode");
	private static final int UNKNOWN_TEAM_SIZE = 0;

	String trigger;
	int teamSize;
	TimedLine time;

	public static StoredBestRun of(StoredPersonalBest best)
	{
		String key = best.getBoss().trim();
		Matcher range = RANGE_SUFFIX.matcher(key);
		if (range.matches())
		{
			return run(range.group(1), UNKNOWN_TEAM_SIZE, best.getSeconds());
		}
		Matcher team = TEAM_SUFFIX.matcher(key);
		if (team.matches())
		{
			return run(team.group(1), teamSizeOf(team.group(2)), best.getSeconds());
		}
		return run(key, UNKNOWN_TEAM_SIZE, best.getSeconds());
	}

	// No digits means solo; a number too long for an int is not a team size the game writes
	private static int teamSizeOf(String digits)
	{
		if (digits == null)
		{
			return 1;
		}
		try
		{
			return Integer.parseInt(digits);
		}
		catch (NumberFormatException tooLong)
		{
			return UNKNOWN_TEAM_SIZE;
		}
	}

	private static StoredBestRun run(String base, int teamSize, double seconds)
	{
		Optional<Kind> raidTime = raidTimeKind(base);
		if (raidTime.isPresent())
		{
			return new StoredBestRun(Triggers.raid(GAME_NAMES.getOrDefault(base, base)), teamSize, new TimedLine(raidTime.get(), seconds));
		}
		return new StoredBestRun(Triggers.kill(base), teamSize, new TimedLine(Kind.SINGLE, seconds));
	}

	private static Optional<Kind> raidTimeKind(String base)
	{
		return RAID_TIME_KINDS.entrySet().stream()
			.filter(raid -> base.startsWith(raid.getKey()))
			.map(Map.Entry::getValue)
			.findFirst();
	}
}
