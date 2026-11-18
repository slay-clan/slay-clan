package com.slayclan.clan;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.WorldType;

/**
 * An allow list of the world flags that leave main-game progress as it is, so a mode this plugin has not
 * seen yet blocks posting until someone has looked at it.
 */
public final class MainGameWorld
{
	private static final Set<WorldType> SHARES_MAIN_GAME_PROGRESS = EnumSet.of(
		WorldType.MEMBERS,
		WorldType.PVP,
		WorldType.BOUNTY,
		WorldType.SKILL_TOTAL,
		WorldType.HIGH_RISK,
		WorldType.HARDCORE_MAIN,
		WorldType.LAST_MAN_STANDING,
		WorldType.PVP_ARENA
	);

	private MainGameWorld()
	{
	}

	public static boolean sharesMainGameProgress(Collection<WorldType> worldTypes)
	{
		return SHARES_MAIN_GAME_PROGRESS.containsAll(worldTypes);
	}
}
