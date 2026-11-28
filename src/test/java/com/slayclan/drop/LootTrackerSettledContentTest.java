package com.slayclan.drop;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LootTrackerSettledContentTest
{
	private final Client client = mock(Client.class);

	@Test
	public void outsideAnyRaidTheCollectionLogMayFallBack()
	{
		assertFalse(LootTrackerSettledContent.insideContentTheLootTrackerSettlesItself(client));
	}

	@Test
	public void insideChambersOfXericTheChestSettlesTheLoot()
	{
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON)).thenReturn(1);

		assertTrue(LootTrackerSettledContent.insideContentTheLootTrackerSettlesItself(client));
	}

	@Test
	public void insideTheatreOfBloodTheChestSettlesTheLoot()
	{
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(2);

		assertTrue(LootTrackerSettledContent.insideContentTheLootTrackerSettlesItself(client));
	}

	@Test
	public void onlyWaitingInATheatreOfBloodPartyOutsideLetsTheCollectionLogFallBack()
	{
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1);

		assertFalse(LootTrackerSettledContent.insideContentTheLootTrackerSettlesItself(client));
	}

	@Test
	public void insideTombsOfAmascutTheSarcophagusSettlesTheLoot()
	{
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS)).thenReturn(1);

		assertTrue(LootTrackerSettledContent.insideContentTheLootTrackerSettlesItself(client));
	}
}
