package com.slayclan.trigger;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

@Value
public class CombatAchievement
{
	String name;
	/** -1 when the game did not print one. */
	int id;
	/** Easy, Medium, Hard, Elite, Master or Grandmaster, as the game names it. */
	String tier;

	/**
	 * The name trigger, plus the id trigger when known, so a task can list either and survive a rename.
	 */
	public List<String> triggers()
	{
		List<String> triggers = new ArrayList<>();
		triggers.add(Triggers.combatAchievement(name));
		if (id >= 0)
		{
			triggers.add(Triggers.combatAchievementId(id));
		}
		return triggers;
	}
}
