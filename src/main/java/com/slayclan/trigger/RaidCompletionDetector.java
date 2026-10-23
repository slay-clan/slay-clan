package com.slayclan.trigger;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

public class RaidCompletionDetector
{
	private static final Pattern RAID_COMPLETION_PATTERN = Pattern.compile("^Your completed (.+) count is: ?[0-9,]+");

	public Optional<String> detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return Optional.empty();
		}
		Matcher matcher = RAID_COMPLETION_PATTERN.matcher(Text.removeTags(chatMessage));
		return matcher.find() ? Optional.of(Triggers.raid(matcher.group(1))) : Optional.empty();
	}
}
