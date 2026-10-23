package com.slayclan.trigger;

import com.slayclan.trigger.TimedLine.Kind;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Reads the time from the lines the game prints after a kill or raid. Personal best text after the time is
 * ignored, so both ". Personal best: x" and "(new personal best)" work. Wave and room durations are skipped.
 */
public class DurationDetector
{
	private static final String TIME = "(\\d+(?::\\d{2})+(?:\\.\\d+)?)";
	private static final Pattern TOTAL_PATTERN = Pattern.compile("(?i)total completion time: " + TIME);
	private static final Pattern CHALLENGE_PATTERN = Pattern.compile("(?i)(?<!total )completion time: " + TIME);
	// Chambers of Xeric prints "Duration", the Nightmare prints "Fight duration", both after the team size
	private static final Pattern RAID_DURATION_PATTERN = Pattern.compile("(?i)Team size: .*?duration: ?" + TIME);
	private static final Pattern SINGLE_PATTERN = Pattern.compile(
		"(?i)^(?:Fight |Colosseum |Corrupted challenge |Challenge |Lap )?duration: " + TIME);

	public Optional<TimedLine> detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return Optional.empty();
		}
		String text = Text.removeTags(chatMessage).trim();
		return find(TOTAL_PATTERN, text, Kind.TOTAL)
			.or(() -> find(CHALLENGE_PATTERN, text, Kind.CHALLENGE))
			.or(() -> find(RAID_DURATION_PATTERN, text, Kind.SINGLE))
			.or(() -> find(SINGLE_PATTERN, text, Kind.SINGLE));
	}

	private static Optional<TimedLine> find(Pattern pattern, String text, Kind kind)
	{
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find())
		{
			return Optional.empty();
		}
		return TimeParser.parseSeconds(matcher.group(1)).stream()
			.mapToObj(seconds -> new TimedLine(kind, seconds))
			.findFirst();
	}
}
