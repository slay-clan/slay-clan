package com.slayclan.trigger;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

public class PersonalBestDetector
{
	private static final String NEW_RECORD = "(new personal best)";
	private static final Pattern CURRENT_BEST = Pattern.compile("(?i)personal best: ?(\\d+(?::\\d{2})+(?:\\.\\d+)?)");

	private final DurationDetector durationDetector = new DurationDetector();

	public Optional<PersonalBest> detect(String chatMessage)
	{
		Optional<TimedLine> run = durationDetector.detect(chatMessage);
		if (run.isEmpty())
		{
			return Optional.empty();
		}
		String text = Text.removeTags(chatMessage);
		if (text.toLowerCase(Locale.ROOT).contains(NEW_RECORD))
		{
			return Optional.of(new PersonalBest(run.get().getKind(), run.get().getSeconds(), run.get().getSeconds(), true));
		}
		Matcher best = CURRENT_BEST.matcher(text);
		if (!best.find())
		{
			return Optional.empty();
		}
		return TimeParser.parseSeconds(best.group(1)).stream()
			.mapToObj(seconds -> new PersonalBest(run.get().getKind(), run.get().getSeconds(), seconds, false))
			.findFirst();
	}
}
