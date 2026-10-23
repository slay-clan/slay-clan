package com.slayclan.trigger;

import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class RaidTeamSizeDetectorTest
{
	private final RaidTeamSizeDetector detector = new RaidTeamSizeDetector();

	@Test
	public void readsTeamSizeFromRaidCompleteMessage()
	{
		assertEquals(Optional.of(2), detector.detect("<col=ef20ff>Congratulations - your raid is complete!</col><br>"
			+ "Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>32:45</col> Personal best: </col><col=ff0000>24:24</col>"));
	}

	@Test
	public void readsSoloAsOnePlayer()
	{
		assertEquals(Optional.of(1), detector.detect("Congratulations - your raid is complete! Team size: Solo Duration: 38:55"));
	}

	@Test
	public void readsLargeTeams()
	{
		assertEquals(Optional.of(11), detector.detect("Congratulations - your raid is complete! Team size: 11-15 players Duration: 40:00"));
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertFalse(detector.detect("Your completed Chambers of Xeric count is: 100.").isPresent());
	}

	@Test
	public void ignoresNullMessage()
	{
		assertFalse(detector.detect(null).isPresent());
	}
}
