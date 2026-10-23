package com.slayclan.activity;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Value;

/**
 * Six tiers in order easy, medium, hard, elite, master, grandmaster. A task in tier i is worth i + 1 points.
 */
@Value
public class CombatAchievementStatus
{
	int[] completedPerTier;
	int[] tierStatus;

	public int points()
	{
		return IntStream.range(0, completedPerTier.length).map(tier -> completedPerTier[tier] * (tier + 1)).sum();
	}

	public String completedText()
	{
		return slashed(completedPerTier);
	}

	public String tierStatusText()
	{
		return slashed(tierStatus);
	}

	private static String slashed(int[] values)
	{
		return IntStream.of(values).mapToObj(String::valueOf).collect(Collectors.joining("/"));
	}
}
