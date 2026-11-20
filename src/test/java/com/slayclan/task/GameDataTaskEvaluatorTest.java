package com.slayclan.task;

import com.slayclan.activity.CompletedAchievement;
import com.slayclan.activity.StoredPersonalBest;
import com.slayclan.config.RemoteConfig;
import com.slayclan.config.TaskDefinition;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GameDataTaskEvaluatorTest
{
	private final GameDataTaskEvaluator evaluator = new GameDataTaskEvaluator(new TaskMatcher(), new TimedTaskFilter());

	@Test
	public void completedCombatAchievementSatisfiesTheTaskNamedByItOrByItsId()
	{
		RemoteConfig config = config(
			once("no-fluke", "CA:It Wasn't a Fluke"),
			once("by-id", "CA:#398"),
			once("other", "CA:Just To Be Safe"));

		List<TaskDefinition> tasks = evaluator.fromCombatAchievements(config,
			List.of(new CompletedAchievement(123, "It Wasn't a Fluke", "Master"), new CompletedAchievement(398, "Into the Den of Giants", "Easy")));

		assertEquals(List.of("no-fluke", "by-id"), ids(tasks));
	}

	@Test
	public void clanOnlyAndRepeatableTasksAreNotSatisfiedByACompletedAchievement()
	{
		TaskDefinition clanOnly = once("perfect-trio", "CA:Perfect Olm (Trio)");
		clanOnly.setClanOnly(true);
		TaskDefinition repeatable = task("again", "CA:Perfect Olm (Trio)");
		RemoteConfig config = config(clanOnly, repeatable);

		assertEquals(List.of(), ids(evaluator.fromCombatAchievements(config, List.of(new CompletedAchievement(1, "Perfect Olm (Trio)", "Master")))));
	}

	@Test
	public void storedBestUnderTheLimitSatisfiesTheSpeedTask()
	{
		TaskDefinition sprint = once("serpent-sprint", "KILL:Zulrah");
		sprint.setMaxTime("1:00");
		TaskDefinition dash = once("dragon-dash", "KILL:Vorkath");
		dash.setMaxTime("1:15");
		RemoteConfig config = config(sprint, dash);

		List<TaskDefinition> tasks = evaluator.fromStoredBests(config,
			List.of(new StoredPersonalBest("zulrah", 43.8, "0:43"), new StoredPersonalBest("vorkath", 80.0, "1:20")));

		assertEquals(List.of("serpent-sprint"), ids(tasks));
	}

	@Test
	public void soloRaidBestSatisfiesTheSoloRaidTaskButNotATeamOne()
	{
		TaskDefinition lone = once("lone-raider", "RAID:Chambers of Xeric*");
		lone.setMaxTime("16:00");
		lone.setTeamSize(1);
		TaskDefinition crew = once("chambers-crew", "RAID:Chambers of Xeric*");
		crew.setMaxTime("35:00");
		crew.setTeamSize(3);
		RemoteConfig config = config(lone, crew);

		List<TaskDefinition> tasks = evaluator.fromStoredBests(config, List.of(new StoredPersonalBest("chambers of xeric solo", 900.0, "15:00")));

		assertEquals(List.of("lone-raider"), ids(tasks));
	}

	@Test
	public void roomTimeTaskIsSatisfiedByAStoredTheatreBest()
	{
		TaskDefinition troupe = once("theatre-troupe", "RAID:Theatre of Blood");
		troupe.setMaxTime("20:00");
		troupe.setTimeType("challenge");
		troupe.setTeamSize(3);

		assertEquals(List.of("theatre-troupe"), ids(evaluator.fromStoredBests(config(troupe),
			List.of(new StoredPersonalBest("theatre of blood 3 players", 900.0, "15:00")))));
	}

	@Test
	public void totalTimeTaskIsNotSatisfiedByAStoredTombsBestSinceThatIsARoomTime()
	{
		TaskDefinition desert = once("desert-dash", "RAID:Tombs of Amascut: Expert Mode");
		desert.setMaxTime("25:00");
		desert.setTimeType("total");
		desert.setTeamSize(1);

		assertEquals(List.of(), ids(evaluator.fromStoredBests(config(desert),
			List.of(new StoredPersonalBest("tombs of amascut expert mode solo", 1400.0, "23:20")))));
	}

	@Test
	public void soloBossTaskIsNotSatisfiedByAStoredBossBestSinceItsTeamSizeIsUnknown()
	{
		TaskDefinition alone = once("yama-alone", "KILL:Yama");
		alone.setMaxTime("3:00");
		alone.setTeamSize(1);
		TaskDefinition any = once("yama-fast", "KILL:Yama");
		any.setMaxTime("3:00");

		assertEquals(List.of("yama-fast"), ids(evaluator.fromStoredBests(config(alone, any),
			List.of(new StoredPersonalBest("yama", 100.0, "1:40")))));
	}

	@Test
	public void oneTimeBossTaskWithoutATimeLimitIsSatisfiedByAnyStoredBest()
	{
		assertEquals(List.of("first-zulrah"), ids(evaluator.fromStoredBests(config(once("first-zulrah", "KILL:Zulrah")),
			List.of(new StoredPersonalBest("zulrah", 100.0, "1:40")))));
	}

	@Test
	public void eachTaskIsListedOnceHoweverManyBestsSatisfyIt()
	{
		TaskDefinition any = once("any-cox", "RAID:Chambers of Xeric*");

		assertEquals(List.of("any-cox"), ids(evaluator.fromStoredBests(config(any), List.of(
			new StoredPersonalBest("chambers of xeric solo", 900.0, "15:00"),
			new StoredPersonalBest("chambers of xeric 3 players", 1200.0, "20:00")))));
	}

	private static RemoteConfig config(TaskDefinition... tasks)
	{
		RemoteConfig config = new RemoteConfig();
		config.setTasks(List.of(tasks));
		return config;
	}

	private static TaskDefinition once(String id, String trigger)
	{
		TaskDefinition task = task(id, trigger);
		task.setOnce(true);
		return task;
	}

	private static TaskDefinition task(String id, String trigger)
	{
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		task.setName(id);
		task.setTrigger(trigger);
		return task;
	}

	private static List<String> ids(List<TaskDefinition> tasks)
	{
		return tasks.stream().map(TaskDefinition::getId).collect(Collectors.toList());
	}
}
