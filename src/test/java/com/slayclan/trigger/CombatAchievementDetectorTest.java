package com.slayclan.trigger;

import java.util.List;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class CombatAchievementDetectorTest
{
	private final CombatAchievementDetector detector = new CombatAchievementDetector();

	@Test
	public void detectsTaskAsTheGamePrintsIt()
	{
		assertEquals(Optional.of(new CombatAchievement("Into the Den of Giants", 398, "Easy")),
			detector.detect("CA_ID:398|Congratulations, you've completed an easy combat task: @ach_comp@Into the Den of Giants</col> (1 point)."));
	}

	@Test
	public void offersNameAndIdAsTriggers()
	{
		assertEquals(List.of("CA:Into the Den of Giants", "CA:#398"), new CombatAchievement("Into the Den of Giants", 398, "Easy").triggers());
		assertEquals(List.of("CA:Perfect Olm (Trio)"), new CombatAchievement("Perfect Olm (Trio)", -1, "Master").triggers());
	}

	@Test
	public void detectsTaskWithColorMarkers()
	{
		assertEquals(Optional.of(new CombatAchievement("Perfect Olm (Trio)", -1, "Master")),
			detector.detect("Congratulations, you've completed a master combat task: @ach_comp@Perfect Olm (Trio)</col>."));
	}

	@Test
	public void stripsPointsSuffix()
	{
		assertEquals(Optional.of(new CombatAchievement("Just To Be Safe", -1, "Elite")),
			detector.detect("Congratulations, you've completed an elite combat task: @ach_comp@Just To Be Safe</col> (5 points)."));
	}

	@Test
	public void detectsTaskWithoutColorMarkers()
	{
		assertEquals(Optional.of(new CombatAchievement("It Wasn't a Fluke", -1, "Grandmaster")),
			detector.detect("Congratulations, you've completed a grandmaster combat task: It Wasn't a Fluke."));
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertFalse(detector.detect("Your Yama kill count is: 12.").isPresent());
	}

	@Test
	public void ignoresNullMessage()
	{
		assertFalse(detector.detect(null).isPresent());
	}
}
