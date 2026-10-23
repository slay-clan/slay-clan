package com.slayclan.trigger;

import com.slayclan.trigger.TimedLine.Kind;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class DurationDetectorTest
{
	private final DurationDetector detector = new DurationDetector();

	@Test
	public void readsTobTotalTimeWithNewPersonalBest()
	{
		assertEquals(Optional.of(new TimedLine(Kind.TOTAL, 3307.2)),
			detector.detect("Theatre of Blood total completion time: <col=ff0000>55:07.20</col> (new personal best)"));
	}

	@Test
	public void readsTobChallengeTime()
	{
		assertEquals(Optional.of(new TimedLine(Kind.CHALLENGE, 1044.6)),
			detector.detect("Theatre of Blood completion time: <col=ff0000>17:24.60</col>. Personal best: 16:16.20"));
	}

	@Test
	public void readsToaChallengeTime()
	{
		assertEquals(Optional.of(new TimedLine(Kind.CHALLENGE, 1718)),
			detector.detect("Tombs of Amascut: Expert Mode challenge completion time: <col=ff0000>28:38</col>. Personal best: 26:43"));
	}

	@Test
	public void readsToaTotalTime()
	{
		assertEquals(Optional.of(new TimedLine(Kind.TOTAL, 1890)),
			detector.detect("Tombs of Amascut total completion time: <col=ff0000>31:30.00</col>. Personal best: 29:57.60"));
	}

	@Test
	public void readsFracturedArchiveTotalTime()
	{
		assertEquals(Optional.of(new TimedLine(Kind.TOTAL, 145.8)),
			detector.detect("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)"));
	}

	@Test
	public void ignoresFracturedArchiveRoomTimes()
	{
		assertFalse(detector.detect(
			"Challenge complete: Ocellus. Duration: <col=ef1020>8:50.40</col>. Total: <col=ef1020>19:07.80</col>. Personal best: 7:52.20").isPresent());
	}

	@Test
	public void readsCoxDurationFromTeamSizeLine()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 1789.2)),
			detector.detect("Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>29:49.20</col> Personal best: 27:45.60 Olm duration: 7:55.2"));
	}

	@Test
	public void readsPhosaniFightDurationAfterTeamSize()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 447.6)),
			detector.detect("Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>7:27.60</col>. Personal best: 5:35.40"));
	}

	@Test
	public void readsFightCavesDurationInHours()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 4090)), detector.detect("Duration: <col=ff0000>1:08:10</col>. Personal best: 1:06:32"));
	}

	@Test
	public void readsFightDuration()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 59)),
			detector.detect("Fight duration: <col=ff0000>0:59</col>. Personal best: 0:55"));
	}

	@Test
	public void readsColosseumDuration()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 2284)), detector.detect("Colosseum duration: 38:04. Personal best: 32:56"));
	}

	@Test
	public void readsCorruptedGauntletDuration()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 389)),
			detector.detect("Corrupted challenge duration: <col=ff0000>6:29</col>. Personal best: 6:10"));
	}

	@Test
	public void readsInfernoDurationOnItsOwnLine()
	{
		assertEquals(Optional.of(new TimedLine(Kind.SINGLE, 6271)), detector.detect("Duration: <col=ff0000>104:31</col>"));
	}

	@Test
	public void ignoresColosseumWaveDuration()
	{
		assertFalse(detector.detect("Wave 12 completed! Wave duration: 38:04").isPresent());
	}

	@Test
	public void ignoresTobFinalRoomDuration()
	{
		assertFalse(detector.detect("Wave 'The Final Challenge' (Normal Mode) complete! Duration: 16:42.00").isPresent());
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertFalse(detector.detect("Your Zulrah kill count is: 5.").isPresent());
	}
}
