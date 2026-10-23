package com.slayclan.activity;

import java.util.stream.IntStream;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

public class CollectionLogReader
{
	private static final int[] CATEGORY_OBTAINED = {
		VarPlayerID.COLLECTION_COUNT_BOSSES, VarPlayerID.COLLECTION_COUNT_RAIDS, VarPlayerID.COLLECTION_COUNT_CLUES,
		VarPlayerID.COLLECTION_COUNT_MINIGAMES, VarPlayerID.COLLECTION_COUNT_OTHER
	};
	private static final int[] CATEGORY_MAX = {
		VarPlayerID.COLLECTION_COUNT_BOSSES_MAX, VarPlayerID.COLLECTION_COUNT_RAIDS_MAX, VarPlayerID.COLLECTION_COUNT_CLUES_MAX,
		VarPlayerID.COLLECTION_COUNT_MINIGAMES_MAX, VarPlayerID.COLLECTION_COUNT_OTHER_MAX
	};

	private final Client client;

	public CollectionLogReader(Client client)
	{
		this.client = client;
	}

	public static boolean readsVarp(int varpId)
	{
		return varpId == VarPlayerID.COLLECTION_COUNT || varpId == VarPlayerID.COLLECTION_COUNT_MAX
			|| contains(CATEGORY_OBTAINED, varpId) || contains(CATEGORY_MAX, varpId);
	}

	private static boolean contains(int[] ids, int id)
	{
		return IntStream.of(ids).anyMatch(candidate -> candidate == id);
	}

	public CollectionLogStatus read()
	{
		return new CollectionLogStatus(
			client.getVarpValue(VarPlayerID.COLLECTION_COUNT),
			client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX),
			varps(CATEGORY_OBTAINED),
			varps(CATEGORY_MAX));
	}

	private int[] varps(int[] ids)
	{
		int[] values = new int[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			values[i] = client.getVarpValue(ids[i]);
		}
		return values;
	}
}
