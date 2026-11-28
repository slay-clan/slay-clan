package com.slayclan.drop;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Raids whose loot the Loot Tracker always reports from their own chest, however long after the collection log line
 * that comes. A collection log fallback there would post the drop a second time, or post loot left in the chest.
 */
public final class LootTrackerSettledContent
{
	// 1 is a party waiting outside the raid, 2 inside it and 3 dead and spectating
	private static final int TOB_INSIDE = 2;

	private LootTrackerSettledContent()
	{
	}

	public static boolean insideContentTheLootTrackerSettlesItself(Client client)
	{
		return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) != 0
			|| client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) >= TOB_INSIDE
			|| client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS) != 0;
	}
}
