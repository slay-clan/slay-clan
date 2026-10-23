package com.slayclan.trigger;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Reads the team size from the Chambers of Xeric completion message. Ranges such as "11-15 players"
 * return their lower bound.
 */
public class RaidTeamSizeDetector
{
	private static final Pattern TEAM_SIZE_PATTERN = Pattern.compile("Team size: (?:(Solo)|(\\d+)[^ ]* players?)");

	public Optional<Integer> detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return Optional.empty();
		}
		Matcher matcher = TEAM_SIZE_PATTERN.matcher(Text.removeTags(chatMessage));
		if (!matcher.find())
		{
			return Optional.empty();
		}
		return Optional.of(matcher.group(1) != null ? 1 : Integer.parseInt(matcher.group(2)));
	}
}
