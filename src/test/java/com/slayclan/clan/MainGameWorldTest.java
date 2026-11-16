package com.slayclan.clan;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MainGameWorldTest
{
	@Test
	public void freeToPlayWorldWithoutAnyFlagCountsAsMainGame()
	{
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.noneOf(WorldType.class)));
	}

	@Test
	public void membersPvpBountyHighRiskAndSkillTotalWorldsCountAsMainGame()
	{
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.BOUNTY)));
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL)));
	}

	@Test
	public void hardcoreWorldsCountAsMainGame()
	{
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)));
	}

	@Test
	public void minigameThemedWorldsCountAsMainGame()
	{
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.LAST_MAN_STANDING)));
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.PVP_ARENA)));
		assertTrue(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.FRESH_START_WORLD)));
	}

	@Test
	public void deadmanLeaguesSpeedrunTournamentAndBetaWorldsDoNotCountAsMainGame()
	{
		assertFalse(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.DEADMAN)));
		assertFalse(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL)));
		assertFalse(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.QUEST_SPEEDRUNNING)));
		assertFalse(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.TOURNAMENT_WORLD)));
		assertFalse(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.BETA_WORLD)));
	}

	@Test
	public void throwawayProgressWorldsDoNotCountAsMainGame()
	{
		assertFalse(MainGameWorld.sharesMainGameProgress(EnumSet.of(WorldType.MEMBERS, WorldType.NOSAVE_MODE)));
	}
}
