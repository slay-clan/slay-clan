package com.slayclan.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads combat achievements from game data. The cache ids come from the game's own script 4834, as used by
 * several Plugin Hub plugins: one enum per tier listing task structs, and params for the task id and name.
 */
public class CombatAchievementReader
{
	static final int[] TIER_ENUMS = {3981, 3982, 3983, 3984, 3985, 3986};
	static final int PARAM_ID = 1306;
	static final int PARAM_NAME = 1308;
	private static final String[] TIER_NAMES = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};
	private static final int[] COMPLETED_PER_TIER = {
		VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY, VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM, VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD,
		VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE, VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER, VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER
	};
	private static final int[] TIER_STATUS = {
		VarbitID.CA_TIER_STATUS_EASY, VarbitID.CA_TIER_STATUS_MEDIUM, VarbitID.CA_TIER_STATUS_HARD,
		VarbitID.CA_TIER_STATUS_ELITE, VarbitID.CA_TIER_STATUS_MASTER, VarbitID.CA_TIER_STATUS_GRANDMASTER
	};
	// Completion flags, 32 tasks per varp, indexed by task id
	private static final int[] COMPLETED_FLAGS = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1, VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3, VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7, VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9, VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13, VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15, VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19, VarPlayerID.CA_TASK_COMPLETED_20
	};

	private final Client client;

	public CombatAchievementReader(Client client)
	{
		this.client = client;
	}

	public static boolean readsVarbit(int varbitId)
	{
		return contains(COMPLETED_PER_TIER, varbitId) || contains(TIER_STATUS, varbitId);
	}

	private static boolean contains(int[] ids, int id)
	{
		return IntStream.of(ids).anyMatch(candidate -> candidate == id);
	}

	public CombatAchievementStatus readStatus()
	{
		return new CombatAchievementStatus(varbits(COMPLETED_PER_TIER), varbits(TIER_STATUS));
	}

	/**
	 * Empty while the cache has not loaded the tier enums yet, which the caller treats as "try again later".
	 */
	public Optional<List<CompletedAchievement>> readCompleted()
	{
		List<CompletedAchievement> completed = new ArrayList<>();
		for (int tier = 0; tier < TIER_ENUMS.length; tier++)
		{
			EnumComposition tasks = client.getEnum(TIER_ENUMS[tier]);
			if (tasks == null)
			{
				return Optional.empty();
			}
			for (int structId : tasks.getIntVals())
			{
				StructComposition task = client.getStructComposition(structId);
				int id = task.getIntValue(PARAM_ID);
				if (isCompleted(id))
				{
					completed.add(new CompletedAchievement(id, task.getStringValue(PARAM_NAME), TIER_NAMES[tier]));
				}
			}
		}
		return Optional.of(completed);
	}

	private boolean isCompleted(int taskId)
	{
		int word = taskId / 32;
		if (word < 0 || word >= COMPLETED_FLAGS.length)
		{
			return false;
		}
		return (client.getVarpValue(COMPLETED_FLAGS[word]) & (1 << (taskId % 32))) != 0;
	}

	private int[] varbits(int[] ids)
	{
		int[] values = new int[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			values[i] = client.getVarbitValue(ids[i]);
		}
		return values;
	}
}
