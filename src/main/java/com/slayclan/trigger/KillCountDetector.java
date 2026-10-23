package com.slayclan.trigger;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

public class KillCountDetector
{
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile("^Your (.+) (?:kill|success|completion) count is: ?[0-9,]+");
	private static final Pattern ANY_COUNT_PATTERN = Pattern.compile("^Your .+ count is: ?([0-9,]+)");

	public Optional<String> detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return Optional.empty();
		}
		Matcher matcher = KILL_COUNT_PATTERN.matcher(Text.removeTags(chatMessage));
		return matcher.find() ? Optional.of(Triggers.kill(matcher.group(1))) : Optional.empty();
	}

	/**
	 * Reads the number from any boss or raid count message.
	 */
	public OptionalInt count(String chatMessage)
	{
		if (chatMessage == null)
		{
			return OptionalInt.empty();
		}
		Matcher matcher = ANY_COUNT_PATTERN.matcher(Text.removeTags(chatMessage));
		return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1).replace(",", ""))) : OptionalInt.empty();
	}
}
