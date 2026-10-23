package com.slayclan.task;

/**
 * Compares task triggers and item names the way players type them: apostrophes cannot be typed in the
 * game chat, so they are ignored along with case.
 */
public final class Names
{
	private static final String WILDCARD = "*";

	private Names()
	{
	}

	public static boolean same(String a, String b)
	{
		return a != null && b != null && normalize(a).equals(normalize(b));
	}

	/**
	 * Like {@link #same}, except that a pattern ending in {@code *} matches every name starting with the rest.
	 */
	public static boolean matches(String pattern, String name)
	{
		if (pattern == null || name == null)
		{
			return false;
		}
		String trimmed = pattern.trim();
		if (!trimmed.endsWith(WILDCARD))
		{
			return same(trimmed, name);
		}
		return normalize(name).startsWith(normalize(trimmed.substring(0, trimmed.length() - WILDCARD.length())));
	}

	public static String normalize(String name)
	{
		return name.replaceAll("['’`]", "").trim().toLowerCase();
	}
}
