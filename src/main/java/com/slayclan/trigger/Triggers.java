package com.slayclan.trigger;

public final class Triggers
{
	public static final String DROP = "DROP";
	public static final String CLOG = "CLOG";
	public static final String LOOT = "LOOT";
	public static final String PET_DUPLICATE = "PET_DUPLICATE";
	public static final String DELVE = "DELVE";
	public static final String EQUIP = "EQUIP";
	public static final String CLOG_COUNT = "CLOG_COUNT";
	public static final String XP_PREFIX = "XP:";
	public static final String HOLD = "HOLD";
	private static final String CONTRACT_PREFIX = "CONTRACT:";
	private static final String KILL_PREFIX = "KILL:";
	private static final String COMBAT_ACHIEVEMENT_PREFIX = "CA:";
	public static final String RAID_PREFIX = "RAID:";

	private Triggers()
	{
	}

	public static String kill(String bossName)
	{
		return KILL_PREFIX + bossName;
	}

	public static String raid(String raidName)
	{
		return RAID_PREFIX + raidName;
	}

	public static String contract(String contractName)
	{
		return CONTRACT_PREFIX + contractName;
	}

	public static String combatAchievement(String taskName)
	{
		return COMBAT_ACHIEVEMENT_PREFIX + taskName;
	}

	public static String combatAchievementId(int id)
	{
		return COMBAT_ACHIEVEMENT_PREFIX + "#" + id;
	}
}
