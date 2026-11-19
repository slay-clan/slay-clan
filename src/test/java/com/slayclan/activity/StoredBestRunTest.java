package com.slayclan.activity;

import com.slayclan.trigger.TimedLine;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class StoredBestRunTest
{
	@Test
	public void bossBestIsAKillWithASingleTimeAndNoTeamSize()
	{
		StoredBestRun run = StoredBestRun.of(new StoredPersonalBest("zulrah", 43.8, "0:43"));

		assertEquals("KILL:zulrah", run.getTrigger());
		assertEquals(0, run.getTeamSize());
		assertEquals(new TimedLine(TimedLine.Kind.SINGLE, 43.8), run.getTime());
	}

	@Test
	public void tombsBestIsTheRoomTimeForTheTeamSizeUnderTheGamesModeName()
	{
		StoredBestRun run = StoredBestRun.of(new StoredPersonalBest("tombs of amascut expert mode 3 players", 1235.4, "20:35"));

		assertEquals("RAID:Tombs of Amascut: Expert Mode", run.getTrigger());
		assertEquals(3, run.getTeamSize());
		assertEquals(new TimedLine(TimedLine.Kind.CHALLENGE, 1235.4), run.getTime());
	}

	@Test
	public void theatreBestIsTheRoomTime()
	{
		assertEquals(new TimedLine(TimedLine.Kind.CHALLENGE, 900.0),
			StoredBestRun.of(new StoredPersonalBest("theatre of blood 2 players", 900.0, "15:00")).getTime());
	}

	@Test
	public void chambersBestIsTheOneDurationTheGamePrints()
	{
		assertEquals(new TimedLine(TimedLine.Kind.SINGLE, 900.0),
			StoredBestRun.of(new StoredPersonalBest("chambers of xeric solo", 900.0, "15:00")).getTime());
	}

	@Test
	public void fracturedArchiveBestIsTheTotalTime()
	{
		assertEquals(new TimedLine(TimedLine.Kind.TOTAL, 145.8),
			StoredBestRun.of(new StoredPersonalBest("fractured archive", 145.8, "2:25")).getTime());
	}

	@Test
	public void soloRaidBestIsATeamOfOne()
	{
		StoredBestRun run = StoredBestRun.of(new StoredPersonalBest("chambers of xeric solo", 900.0, "15:00"));

		assertEquals("RAID:chambers of xeric", run.getTrigger());
		assertEquals(1, run.getTeamSize());
	}

	@Test
	public void raidBestStoredForATeamSizeRangeOrWithoutOneHasNoTeamSize()
	{
		assertEquals(0, StoredBestRun.of(new StoredPersonalBest("chambers of xeric 11-15 players", 900.0, "15:00")).getTeamSize());
		assertEquals(0, StoredBestRun.of(new StoredPersonalBest("chambers of xeric 24+ players", 900.0, "15:00")).getTeamSize());
		assertEquals(0, StoredBestRun.of(new StoredPersonalBest("theatre of blood hard mode", 900.0, "15:00")).getTeamSize());
	}

	@Test
	public void everyRaidModeGetsTheColonTheGameNamesItWith()
	{
		assertEquals("RAID:Tombs of Amascut: Entry Mode", trigger("tombs of amascut entry mode solo"));
		assertEquals("RAID:Tombs of Amascut: Expert Mode", trigger("tombs of amascut expert mode solo"));
		assertEquals("RAID:Theatre of Blood: Entry Mode", trigger("theatre of blood entry mode 2 players"));
		assertEquals("RAID:Theatre of Blood: Hard Mode", trigger("theatre of blood hard mode 2 players"));
	}

	@Test
	public void challengeModeKeepsTheGamesNameWithoutAColon()
	{
		assertEquals("RAID:chambers of xeric challenge mode", trigger("chambers of xeric challenge mode 3 players"));
	}

	private static String trigger(String key)
	{
		return StoredBestRun.of(new StoredPersonalBest(key, 900.0, "15:00")).getTrigger();
	}
}
