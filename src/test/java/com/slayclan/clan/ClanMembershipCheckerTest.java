package com.slayclan.clan;

import java.util.List;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClanMembershipCheckerTest
{
	private final ClanMembershipChecker checker = new ClanMembershipChecker();
	private ClanSettings clan;

	@Before
	public void setUp()
	{
		clan = mock(ClanSettings.class);
		when(clan.getName()).thenReturn("SlayClan");
		when(clan.findMember("Clanmate")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Iron Clanmate")).thenReturn(mock(ClanMember.class));
	}

	@Test
	public void acceptsPartyWhereEveryoneIsAMember()
	{
		assertTrue(checker.allMembers(clan, "SlayClan", List.of("Clanmate", "Raidbuddy")));
	}

	@Test
	public void rejectsPartyWithAnOutsider()
	{
		assertFalse(checker.allMembers(clan, "SlayClan", List.of("Clanmate", "Zezima")));
	}

	@Test
	public void rejectsWhenLoggedIntoAnotherClan()
	{
		assertFalse(checker.allMembers(clan, "OtherClan", List.of("Clanmate", "Raidbuddy")));
	}

	@Test
	public void comparesClanNameIgnoringCase()
	{
		assertTrue(checker.allMembers(clan, "slayclan", List.of("Clanmate")));
	}

	@Test
	public void rejectsWhenClanSettingsAreMissing()
	{
		assertFalse(checker.allMembers(null, "SlayClan", List.of("Clanmate")));
	}

	@Test
	public void recognizesExpectedClanWithoutCheckingPlayers()
	{
		assertTrue(checker.isClan(clan, "SlayClan"));
	}

	@Test
	public void rejectsOtherClanName()
	{
		assertFalse(checker.isClan(clan, "OtherClan"));
	}

	@Test
	public void rejectsClanCheckWithoutClanSettings()
	{
		assertFalse(checker.isClan(null, "SlayClan"));
	}

	@Test
	public void normalizesNonBreakingSpacesInNames()
	{
		assertTrue(checker.allMembers(clan, "SlayClan", List.of("Iron Clanmate")));
	}
}
