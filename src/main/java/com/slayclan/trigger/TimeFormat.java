package com.slayclan.trigger;

/**
 * Formats seconds the way the game prints times: m:ss, with hundredths only when present.
 */
public final class TimeFormat
{
	private TimeFormat()
	{
	}

	public static String of(double seconds)
	{
		long hundredths = Math.round(seconds * 100);
		long minutes = hundredths / 6000;
		long rest = hundredths % 6000;
		long wholeSeconds = rest / 100;
		long fraction = rest % 100;
		String base = minutes + ":" + String.format("%02d", wholeSeconds);
		return fraction == 0 ? base : base + String.format(".%02d", fraction);
	}
}
