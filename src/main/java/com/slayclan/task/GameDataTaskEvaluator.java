package com.slayclan.task;

import com.slayclan.activity.CompletedAchievement;
import com.slayclan.activity.StoredBestRun;
import com.slayclan.activity.StoredPersonalBest;
import com.slayclan.config.RemoteConfig;
import com.slayclan.config.TaskDefinition;
import com.slayclan.trigger.Triggers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One-time tasks the game's own records already satisfy, so a member who did the work before installing the
 * plugin still gets them. Clan-only tasks are left out: who was in the team cannot be told afterwards.
 */
public class GameDataTaskEvaluator
{
	// The game keeps no kill count with a stored best, so a task asking for one is left to the live kill
	private static final int UNKNOWN_KILL_COUNT = 0;

	private final TaskMatcher taskMatcher;
	private final TimedTaskFilter timedTaskFilter;

	public GameDataTaskEvaluator(TaskMatcher taskMatcher, TimedTaskFilter timedTaskFilter)
	{
		this.taskMatcher = taskMatcher;
		this.timedTaskFilter = timedTaskFilter;
	}

	public List<SatisfiedTask> fromCombatAchievements(RemoteConfig config, List<CompletedAchievement> completed)
	{
		return eachOnce(completed.stream().flatMap(achievement -> taskMatcher.match(config,
			List.of(Triggers.combatAchievement(achievement.getName()), Triggers.combatAchievementId(achievement.getId())), 1).stream()
			.map(task -> new SatisfiedTask(task, "Combat achievement: " + achievement.getName()))));
	}

	public List<SatisfiedTask> fromStoredBests(RemoteConfig config, List<StoredPersonalBest> bests)
	{
		return eachOnce(bests.stream().flatMap(best -> satisfiedBy(config, best)));
	}

	private Stream<SatisfiedTask> satisfiedBy(RemoteConfig config, StoredPersonalBest best)
	{
		StoredBestRun run = StoredBestRun.of(best);
		return taskMatcher.match(config, run.getTrigger(), Math.max(run.getTeamSize(), 1)).stream()
			.filter(task -> timedTaskFilter.accepts(task, List.of(run.getTime()), run.getTeamSize(), UNKNOWN_KILL_COUNT))
			.map(task -> new SatisfiedTask(task, "Personal best: " + best.getBoss() + " " + best.getTime()));
	}

	private static List<SatisfiedTask> eachOnce(Stream<SatisfiedTask> satisfied)
	{
		Map<String, SatisfiedTask> byId = new LinkedHashMap<>();
		satisfied.filter(found -> judgedFromGameData(found.getTask())).forEach(found -> byId.putIfAbsent(found.getTask().getId(), found));
		return List.copyOf(byId.values());
	}

	private static boolean judgedFromGameData(TaskDefinition task)
	{
		return task.isOnce() && !task.isClanOnly();
	}
}
