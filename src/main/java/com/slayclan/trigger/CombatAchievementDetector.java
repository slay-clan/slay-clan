package com.slayclan.trigger;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

public class CombatAchievementDetector
{
	// The game prefixes the line with "CA_ID:<id>|", wraps the name in an @color@ marker and appends " (N points)"
	private static final Pattern COMBAT_TASK_PATTERN = Pattern.compile(
		"^(?:CA_ID:(\\d+)\\|)?Congratulations, you've completed an? (\\w+) combat task: (?:@[^@]+@)?(.+?)(?: \\(\\d+ points?\\))?\\.?$");

	public Optional<CombatAchievement> detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return Optional.empty();
		}
		Matcher matcher = COMBAT_TASK_PATTERN.matcher(Text.removeTags(chatMessage).trim());
		if (!matcher.matches())
		{
			return Optional.empty();
		}
		int id = matcher.group(1) == null ? -1 : Integer.parseInt(matcher.group(1));
		return Optional.of(new CombatAchievement(matcher.group(3).trim(), id, capitalised(matcher.group(2))));
	}

	private static String capitalised(String word)
	{
		return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
	}
}
