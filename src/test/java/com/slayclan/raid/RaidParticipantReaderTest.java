package com.slayclan.raid;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RaidParticipantReaderTest
{
	private final Client client = mock(Client.class);
	private final RaidParticipantReader reader = new RaidParticipantReader(client);

	@Test
	public void fracturedArchivePartyComesFromTheGamesPartyList()
	{
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0)).thenReturn("Clanmate");
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME1)).thenReturn("<col=ff0000>Raidbuddy</col>");
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME2)).thenReturn("");

		assertEquals(List.of("Clanmate", "Raidbuddy"), reader.read("RAID:Fractured Archive"));
	}

	@Test
	public void partyNamesUseOrdinarySpacesEvenWhenTheGameSendsNonBreakingOnes()
	{
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0)).thenReturn("Clan mate");

		assertEquals(List.of("Clan mate"), reader.read("RAID:Fractured Archive"));
	}

	@Test
	public void fracturedArchiveTeamSizeCountsTheFilledPartySlotsPlusThePlayer()
	{
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P1)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P2)).thenReturn(1);

		assertEquals(3, reader.gamesTeamSize("RAID:Fractured Archive"));
	}

	@Test
	public void otherRaidsStateNoTeamSizeThroughTheReader()
	{
		assertEquals(0, reader.gamesTeamSize("RAID:Tombs of Amascut"));
	}

	@Test
	public void tombsOfAmascutPartyComesFromTheGamesPartyList()
	{
		when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME0)).thenReturn("Clanmate");
		when(client.getVarcStrValue(VarClientID.TOA_CLIENT_NAME3)).thenReturn("Third");

		assertEquals(List.of("Clanmate", "Third"), reader.read("RAID:Tombs of Amascut: Expert Mode"));
	}
}
