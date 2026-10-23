package com.slayclan.trigger;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Reads the level of a completed Doom of Mokhaiotl delve. Deep levels are printed as "8+ (22)". The same line
 * without "duration" is shown when a level starts and is ignored.
 */
public class DelveDetector
{
	private static final Pattern DELVE_PATTERN = Pattern.compile("^Delve level: (\\d+)(?:\\+ \\((\\d+)\\))? duration:");

	public OptionalInt detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return OptionalInt.empty();
		}
		Matcher matcher = DELVE_PATTERN.matcher(Text.removeTags(chatMessage).trim());
		if (!matcher.find())
		{
			return OptionalInt.empty();
		}
		return OptionalInt.of(Integer.parseInt(matcher.group(2) != null ? matcher.group(2) : matcher.group(1)));
	}
}
