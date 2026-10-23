package com.slayclan.trigger;

import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class RaidCompletionDetectorTest
{
	private final RaidCompletionDetector detector = new RaidCompletionDetector();

	@Test
	public void detectsChambersOfXeric()
	{
		assertEquals(Optional.of("RAID:Chambers of Xeric"),
			detector.detect("Your completed Chambers of Xeric count is: <col=ff0000>100</col>."));
	}

	@Test
	public void detectsTheatreOfBlood()
	{
		assertEquals(Optional.of("RAID:Theatre of Blood"), detector.detect("Your completed Theatre of Blood count is: 12."));
	}

	@Test
	public void detectsTheFracturedArchive()
	{
		assertEquals(Optional.of("RAID:Fractured Archive"),
			detector.detect("Your completed Fractured Archive count is: <col=ff0000>1</col>."));
	}

	@Test
	public void keepsRaidModeInName()
	{
		assertEquals(Optional.of("RAID:Tombs of Amascut: Expert Mode"),
			detector.detect("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>3</col>."));
	}

	@Test
	public void ignoresBossKillCount()
	{
		assertFalse(detector.detect("Your Yama kill count is: 12.").isPresent());
	}

	@Test
	public void ignoresNullMessage()
	{
		assertFalse(detector.detect(null).isPresent());
	}
}
