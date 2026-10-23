package com.slayclan.trigger;

import com.slayclan.trigger.TimedLine.Kind;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class PersonalBestDetectorTest
{
	private final PersonalBestDetector detector = new PersonalBestDetector();

	@Test
	public void readsANewRecord()
	{
		assertEquals(Optional.of(new PersonalBest(Kind.TOTAL, 3307.2, 3307.2, true)),
			detector.detect("Theatre of Blood total completion time: <col=ff0000>55:07.20</col> (new personal best)"));
	}

	@Test
	public void readsTheCurrentBestAfterAnOrdinaryKill()
	{
		assertEquals(Optional.of(new PersonalBest(Kind.SINGLE, 59, 55, false)),
			detector.detect("Fight duration: <col=ff0000>0:59</col>. Personal best: 0:55"));
	}

	@Test
	public void readsTheCoxLine()
	{
		assertEquals(Optional.of(new PersonalBest(Kind.SINGLE, 1789.2, 1665.6, false)),
			detector.detect("Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>29:49.20</col> Personal best: 27:45.60 Olm duration: 7:55.2"));
	}

	@Test
	public void ignoresTimesWithoutAPersonalBest()
	{
		assertFalse(detector.detect("Duration: <col=ff0000>104:31</col>").isPresent());
	}

	@Test
	public void ignoresRoomTimes()
	{
		assertFalse(detector.detect("Wave 'The Maiden of Sugadinti' complete! Duration: 2:00.00 (new personal best)").isPresent());
	}
}
