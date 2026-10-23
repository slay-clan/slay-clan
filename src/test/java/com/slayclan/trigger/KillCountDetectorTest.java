package com.slayclan.trigger;

import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class KillCountDetectorTest
{
	private final KillCountDetector detector = new KillCountDetector();

	@Test
	public void detectsBossKillWithColorTags()
	{
		assertEquals(Optional.of("KILL:Yama"), detector.detect("Your Yama kill count is: <col=ff0000>12</col>."));
	}

	@Test
	public void detectsMultiWordBossName()
	{
		assertEquals(Optional.of("KILL:Phantom Muspah"), detector.detect("Your Phantom Muspah kill count is: 1,204."));
	}

	@Test
	public void detectsSuccessCount()
	{
		assertEquals(Optional.of("KILL:Gauntlet"), detector.detect("Your Gauntlet success count is: <col=ff0000>3</col>."));
	}

	@Test
	public void detectsCompletionCount()
	{
		assertEquals(Optional.of("KILL:Corrupted Gauntlet"),
			detector.detect("Your Corrupted Gauntlet completion count is: <col=ff0000>12</col>."));
	}

	@Test
	public void readsKillCountNumber()
	{
		assertEquals(OptionalInt.of(1204), detector.count("Your Phantom Muspah kill count is: <col=ff0000>1,204</col>."));
	}

	@Test
	public void readsRaidCompletionCountNumber()
	{
		assertEquals(OptionalInt.of(2649), detector.count("Your completed Chambers of Xeric Challenge Mode count is: 2,649."));
	}

	@Test
	public void returnsNoCountForOtherMessages()
	{
		assertFalse(detector.count("Welcome to Old School RuneScape.").isPresent());
	}

	@Test
	public void ignoresCoxCompletion()
	{
		assertFalse(detector.detect("Your completed Chambers of Xeric count is: <col=ff0000>42</col>.").isPresent());
	}

	@Test
	public void ignoresUnrelatedMessage()
	{
		assertFalse(detector.detect("Welcome to Old School RuneScape.").isPresent());
	}

	@Test
	public void ignoresNullMessage()
	{
		assertFalse(detector.detect(null).isPresent());
	}
}
