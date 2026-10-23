package com.slayclan.trigger;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser
{
	private static final Pattern TIME_PATTERN = Pattern.compile("^(?:(\\d+):)?(\\d+):(\\d{2})(?:\\.(\\d{1,2}))?$");

	private TimeParser()
	{
	}

	/**
	 * Parses in-game times such as {@code 0:59}, {@code 1:08.4}, {@code 36:42.60} or {@code 1:02:03}.
	 */
	public static OptionalDouble parseSeconds(String time)
	{
		if (time == null)
		{
			return OptionalDouble.empty();
		}
		Matcher matcher = TIME_PATTERN.matcher(time.trim());
		if (!matcher.matches())
		{
			return OptionalDouble.empty();
		}
		long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
		long wholeSeconds = hours * 3600 + Long.parseLong(matcher.group(2)) * 60 + Long.parseLong(matcher.group(3));
		String fraction = matcher.group(4) == null ? "0" : (matcher.group(4) + "0").substring(0, 2);
		return OptionalDouble.of((wholeSeconds * 100 + Long.parseLong(fraction)) / 100.0);
	}
}
