package com.slayclan.trigger;

import java.util.OptionalInt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class DelveDetectorTest
{
	private final DelveDetector detector = new DelveDetector();

	@Test
	public void readsShallowLevelCompletion()
	{
		assertEquals(OptionalInt.of(2), detector.detect("Delve level: 2 duration: <col=ff0000>1:00</col>. Personal best: 0:26"));
	}

	@Test
	public void readsDeepLevelFromParentheses()
	{
		assertEquals(OptionalInt.of(22), detector.detect("Delve level: 8+ (22) duration: <col=ff0000>1:08.4</col>. Personal best: 0:54.0"));
	}

	@Test
	public void ignoresLevelStartWithoutDuration()
	{
		assertFalse(detector.detect("Delve level: 8+ (26)").isPresent());
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertFalse(detector.detect("Total duration: 1:34").isPresent());
		assertFalse(detector.detect(null).isPresent());
	}
}
