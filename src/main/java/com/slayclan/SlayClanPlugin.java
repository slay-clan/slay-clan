package com.slayclan;

import com.slayclan.activity.ActivityFormatter;
import com.slayclan.activity.ActivityReporter;
import com.slayclan.activity.CollectionLogCapture;
import com.slayclan.activity.CollectionLogReader;
import com.slayclan.activity.CombatAchievementReader;
import com.slayclan.activity.DeliveredSnapshots;
import com.slayclan.activity.ItemNameIndex;
import com.slayclan.activity.OwnedItem;
import com.slayclan.activity.PersonalBestClaim;
import com.slayclan.activity.PersonalBestReader;
import com.slayclan.activity.ScaleBestStore;
import com.slayclan.clan.ClanMembershipChecker;
import com.slayclan.config.RemoteConfig;
import com.slayclan.config.RemoteConfigService;
import com.slayclan.config.SecretSafeUrl;
import com.slayclan.config.TaskDefinition;
import com.slayclan.discord.BotClient;
import com.slayclan.discord.BotReply;
import com.slayclan.discord.BotTarget;
import com.slayclan.discord.MessageFormatter;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostPayload;
import com.slayclan.discord.ScreenshotService;
import com.slayclan.drop.DropEvaluator;
import com.slayclan.drop.PricedItem;
import com.slayclan.drop.ValuableDrop;
import com.slayclan.progress.TaskProgressStore;
import com.slayclan.raid.RaidParticipantReader;
import com.slayclan.task.ClogTaskEvaluator;
import com.slayclan.task.LootTaskEvaluator;
import com.slayclan.task.Names;
import com.slayclan.task.ThresholdTaskEvaluator;
import com.slayclan.task.TimedTaskFilter;
import com.slayclan.trigger.CollectionLogDetector;
import com.slayclan.trigger.CombatAchievement;
import com.slayclan.trigger.CombatAchievementDetector;
import com.slayclan.trigger.DuplicatePetDetector;
import com.slayclan.task.TaskMatcher;
import com.slayclan.trigger.KillCountDetector;
import com.slayclan.trigger.PersonalBest;
import com.slayclan.trigger.PersonalBestDetector;
import com.slayclan.trigger.TimeFormat;
import com.slayclan.trigger.ContractTracker;
import com.slayclan.trigger.DelveDetector;
import com.slayclan.trigger.DurationDetector;
import com.slayclan.trigger.RaidCompletionDetector;
import com.slayclan.trigger.TimedLine;
import com.slayclan.trigger.RaidTeamSizeDetector;
import com.slayclan.trigger.Triggers;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.util.ColorUtil;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slay Clan",
	description = "Posts completed Slay Clan tasks to the clan Discord",
	tags = {"clan", "discord", "raids", "cox"}
)
public class SlayClanPlugin extends Plugin
{
	private static final long CONFIG_REFRESH_MINUTES = 10;
	// Lets the player finish typing a URL before it is fetched
	private static final long CONFIG_CHANGE_DELAY_SECONDS = 2;
	// Gives the config fetch time to finish before judging whether it failed
	private static final int SETUP_WARNING_DELAY_TICKS = 15;
	// Loot Tracker reports opened PvP loot keys under this name
	private static final String PVP_LOOT_CHEST = "Loot Chest";
	// The team size line is printed a moment before the completion count
	private static final int TEAM_SIZE_MAX_AGE_TICKS = 5;
	// Kill count, time and team size lines can arrive in any order across neighbouring ticks
	private static final int COMPLETION_DELAY_TICKS = 2;
	private static final int RECENT_TIME_MAX_AGE_TICKS = 10;
	// Stats, equipment and the collection log count load during the first ticks after login
	private static final int BASELINE_DELAY_TICKS = 5;
	// Backup for ActorDeath when clearing a used Yama contract
	private static final String DEATH_MESSAGE = "Oh dear, you are dead!";
	// Game messages containing these are logged when no detector recognises them, to diagnose changed texts
	private static final List<String> TASK_RELATED_PHRASES = List.of(
		"count is", "collection log", "combat task", "duration", "completion time", "delve level", "funny feeling", "backpack");
	// The varbit combines two game settings: 1 = "New addition notification" (chat), 2 = "New addition popup"
	private static final int COLLECTION_LOG_CHAT_BIT = 1;
	private static final Set<String> CLAN_CONFIG_KEYS = Set.of(SlayClanConfig.CONFIG_URL_KEY, SlayClanConfig.ENABLED_KEY);
	private static final Set<String> POSTING_KEYS = Set.of(
		SlayClanConfig.CONFIG_URL_KEY, SlayClanConfig.ENABLED_KEY, SlayClanConfig.CLAN_TOKEN_KEY, SlayClanConfig.BOT_URL_KEY);
	private static final String RETIRED_DISCORD_NAME_KEY = "discordName";

	@Inject
	private Client client;

	@Inject
	private SlayClanConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private RemoteConfigService remoteConfigService;

	@Inject
	private RaidParticipantReader participantReader;

	@Inject
	private ScreenshotService screenshotService;

	@Inject
	private BotClient botClient;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private TaskProgressStore progressStore;

	@Inject
	private PendingPostStore pendingPostStore;

	@Inject
	private Gson gson;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	private final RaidCompletionDetector raidCompletionDetector = new RaidCompletionDetector();
	private final RaidTeamSizeDetector raidTeamSizeDetector = new RaidTeamSizeDetector();
	private final DurationDetector durationDetector = new DurationDetector();
	private final DelveDetector delveDetector = new DelveDetector();
	private final TimedTaskFilter timedTaskFilter = new TimedTaskFilter();
	private final ThresholdTaskEvaluator thresholdTaskEvaluator = new ThresholdTaskEvaluator();
	private final ContractTracker contractTracker = new ContractTracker();
	private final List<PendingCompletion> pendingCompletions = new ArrayList<>();
	private final List<TickedTime> recentTimes = new ArrayList<>();
	private int lastRaidTeamSize;
	private int lastRaidTeamSizeTick = Integer.MIN_VALUE / 2;
	private final KillCountDetector killCountDetector = new KillCountDetector();
	private final TaskMatcher taskMatcher = new TaskMatcher();
	private final ClanMembershipChecker membershipChecker = new ClanMembershipChecker();
	private final MessageFormatter messageFormatter = new MessageFormatter();
	private final DropEvaluator dropEvaluator = new DropEvaluator();
	private final CollectionLogDetector collectionLogDetector = new CollectionLogDetector();
	private final ClogTaskEvaluator clogTaskEvaluator = new ClogTaskEvaluator();
	private final CombatAchievementDetector combatAchievementDetector = new CombatAchievementDetector();
	private final LootTaskEvaluator lootTaskEvaluator = new LootTaskEvaluator();
	private final DuplicatePetDetector duplicatePetDetector = new DuplicatePetDetector();

	private ScheduledFuture<?> refreshTask;
	private ScheduledFuture<?> configChangeRefresh;
	private final AtomicBoolean retryInProgress = new AtomicBoolean();
	private boolean loginPending;
	private final PersonalBestDetector personalBestDetector = new PersonalBestDetector();
	private final List<TickedPersonalBest> recentPersonalBests = new ArrayList<>();
	private final Set<String> bossesReportedThisLogin = new HashSet<>();
	private CombatAchievementReader combatAchievementReader;
	private CollectionLogReader collectionLogReader;
	private CollectionLogCapture collectionLogCapture;
	private ActivityFormatter activityFormatter;
	private ActivityReporter activityReporter;
	private DeliveredSnapshots deliveredSnapshots;
	private ScaleBestStore scaleBestStore;
	private ItemNameIndex itemNameIndex;
	private boolean statusCheckPending;
	private boolean caSnapshotPending;
	private boolean pbSnapshotPending;
	private PersonalBestReader personalBestReader;
	// Logged once per login, as every event would otherwise repeat it
	private boolean postingBlockedLogged;
	// Each said once per login and once per settings change, as every post would otherwise repeat it
	private final Set<BotReply.Blocker> blockersSaid = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean clanConfigChanged = new AtomicBoolean();
	private boolean clogSettingCheckPending;
	private boolean setupWarningPending;
	private boolean thresholdCheckPending;
	private int loginTick;

	@Override
	protected void startUp()
	{
		combatAchievementReader = new CombatAchievementReader(client);
		collectionLogReader = new CollectionLogReader(client);
		collectionLogCapture = new CollectionLogCapture(client, itemManager, this::canPostForClan);
		personalBestReader = new PersonalBestReader(configManager);
		activityFormatter = new ActivityFormatter(gson);
		deliveredSnapshots = new DeliveredSnapshots(configManager);
		scaleBestStore = new ScaleBestStore(configManager);
		itemNameIndex = new ItemNameIndex(client);
		activityReporter = new ActivityReporter(activityFormatter, botClient, pendingPostStore, deliveredSnapshots, this::tell);
		forgetTheRetiredDiscordNameSetting();
		refreshTask = executor.scheduleAtFixedRate(
			() -> refreshRemoteConfig().thenRun(this::retryPendingPosts), 0, CONFIG_REFRESH_MINUTES, TimeUnit.MINUTES);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			scheduleLoginChecks();
		}
		else if (client.getGameState() == GameState.LOADING)
		{
			loginPending = true;
		}
	}

	@Override
	protected void shutDown()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
		if (configChangeRefresh != null)
		{
			configChangeRefresh.cancel(false);
			configChangeRefresh = null;
		}
		botClient.cancelRetries();
		remoteConfigService.refresh(null);
		pendingCompletions.clear();
		recentTimes.clear();
		recentPersonalBests.clear();
		bossesReportedThisLogin.clear();
		lastRaidTeamSizeTick = Integer.MIN_VALUE / 2;
		statusCheckPending = false;
		caSnapshotPending = false;
		pbSnapshotPending = false;
		clogSettingCheckPending = false;
		thresholdCheckPending = false;
		setupWarningPending = false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!changesWherePostsGo(event))
		{
			return;
		}
		if (configChangeRefresh != null)
		{
			configChangeRefresh.cancel(false);
		}
		// Rate limit retries waiting for the old address or token are kept and resent with the new settings
		botClient.cancelRetries();
		blockersSaid.clear();
		postingBlockedLogged = false;
		if (CLAN_CONFIG_KEYS.contains(event.getKey()))
		{
			clanConfigChanged.set(true);
		}
		configChangeRefresh = executor.schedule(this::followSettingsChange, CONFIG_CHANGE_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	private static boolean changesWherePostsGo(ConfigChanged event)
	{
		return SlayClanConfig.GROUP.equals(event.getGroup()) && POSTING_KEYS.contains(event.getKey());
	}

	// Only the clan config URL and the posting switch change which clan config applies
	private void followSettingsChange()
	{
		clientThread.invoke(this::warnAboutTheBotUrlWhileLoggedIn);
		if (clanConfigChanged.getAndSet(false))
		{
			refreshRemoteConfig().thenRun(this::retryPendingPosts);
		}
		else
		{
			retryPendingPosts();
		}
	}

	private void warnAboutTheBotUrlWhileLoggedIn()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			warnIfBotUrlUnusable();
		}
	}

	private void warnIfBotUrlUnusable()
	{
		if (config.enabled() && !hasSecretSafeBotUrl())
		{
			warnInChat("Slay Clan: paste the clan bot URL from the clan Discord into the plugin settings, otherwise nothing is posted. It must start with https://.");
		}
	}

	/**
	 * LOGGED_IN also follows every LOADING, so only a LOGGED_IN that comes after logging in, hopping or
	 * reconnecting counts as a login.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				loginPending = true;
				break;
			case LOGGED_IN:
				if (loginPending)
				{
					loginPending = false;
					onLogin();
				}
				break;
			default:
				break;
		}
	}

	private void onLogin()
	{
		refreshRemoteConfig();
		scheduleLoginChecks();
	}

	private void scheduleLoginChecks()
	{
		postingBlockedLogged = false;
		blockersSaid.clear();
		statusCheckPending = true;
		caSnapshotPending = true;
		pbSnapshotPending = true;
		bossesReportedThisLogin.clear();
		collectionLogCapture.onLogin();
		clogSettingCheckPending = true;
		thresholdCheckPending = true;
		setupWarningPending = true;
		loginTick = client.getTickCount();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			thresholdCheckPending = true;
		}
		if (event.getContainerId() == InventoryID.INV)
		{
			thresholdCheckPending = true;
			contractTracker.onInventory(itemNames(event.getItemContainer()), client.getTickCount());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			contractTracker.onDeath();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		thresholdCheckPending = true;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (changesProgressStatus(event))
		{
			statusCheckPending = true;
		}
		if (event.getVarpId() == VarPlayerID.COLLECTION_COUNT)
		{
			thresholdCheckPending = true;
		}
	}

	private static boolean changesProgressStatus(VarbitChanged event)
	{
		return CombatAchievementReader.readsVarbit(event.getVarbitId()) || CollectionLogReader.readsVarp(event.getVarpId());
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		collectionLogCapture.onScriptPostFired(event);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		collectionLogCapture.onScriptPreFired(event);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		processMessage(event.getMessage());
	}

	private void processMessage(String message)
	{
		int tick = client.getTickCount();
		boolean recognized = false;
		Optional<Integer> teamSize = raidTeamSizeDetector.detect(message);
		if (teamSize.isPresent())
		{
			lastRaidTeamSize = teamSize.get();
			lastRaidTeamSizeTick = tick;
			recognized = true;
		}
		Optional<TimedLine> time = durationDetector.detect(message);
		if (time.isPresent())
		{
			recentTimes.add(new TickedTime(time.get(), tick));
			recognized = true;
		}
		OptionalInt delveLevel = delveDetector.detect(message);
		if (delveLevel.isPresent())
		{
			handleDelve(delveLevel.getAsInt());
			recognized = true;
		}
		if (Text.removeTags(message).startsWith(DEATH_MESSAGE))
		{
			contractTracker.onDeath();
		}
		int killCount = killCountDetector.count(message).orElse(0);
		Optional<String> killTrigger = killCountDetector.detect(message).filter(this::notSwitchedOff);
		killTrigger.ifPresent(trigger ->
		{
			queueCompletion(trigger, killCount);
			contractTracker.onKill(trigger, tick).ifPresent(this::handlePersonalTrigger);
		});
		Optional<String> raidTrigger = raidCompletionDetector.detect(message).filter(this::notSwitchedOff);
		raidTrigger.ifPresent(trigger -> queueCompletion(trigger, killCount));
		Optional<String> clogItem = collectionLogDetector.detect(message);
		clogItem.ifPresent(this::handleCollectionLog);
		clogItem.ifPresent(this::reportCollectionLogItem);
		personalBestDetector.detect(message).ifPresent(best -> recentPersonalBests.add(new TickedPersonalBest(best, tick)));
		Optional<CombatAchievement> achievement = combatAchievementDetector.detect(message);
		achievement.ifPresent(found ->
		{
			log.info("Combat achievement '{}' has id {}", found.getName(), found.getId());
			reportCombatAchievement(found);
			handlePersonalTrigger(found.triggers());
		});
		Optional<String> personalTrigger = achievement.isPresent() ? Optional.empty() : duplicatePetDetector.detect(message);
		personalTrigger.ifPresent(this::handlePersonalTrigger);

		recognized = recognized || killTrigger.isPresent() || raidTrigger.isPresent()
			|| clogItem.isPresent() || achievement.isPresent() || personalTrigger.isPresent();
		if (!recognized && looksTaskRelated(message))
		{
			log.info("Unrecognized game message that may belong to a clan task: {}", Text.removeTags(message));
		}
	}

	private boolean notSwitchedOff(String trigger)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (remoteConfig == null || !remoteConfig.ignores(bossName(trigger)))
		{
			return true;
		}
		log.debug("{} is switched off in the clan config, nothing posted", bossName(trigger));
		return false;
	}

	private static boolean looksTaskRelated(String message)
	{
		String text = Text.removeTags(message).toLowerCase();
		return !text.contains("wave") && !text.startsWith("challenge complete:")
			&& TASK_RELATED_PHRASES.stream().anyMatch(text::contains);
	}

	private void queueCompletion(String trigger)
	{
		queueCompletion(trigger, 0);
	}

	private void queueCompletion(String trigger, int killCount)
	{
		pendingCompletions.add(new PendingCompletion(trigger, client.getTickCount(), killCount));
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (clogSettingCheckPending)
		{
			warnIfCollectionLogChatDisabled();
		}
		if (setupWarningPending && client.getTickCount() - loginTick >= SETUP_WARNING_DELAY_TICKS)
		{
			warnAboutSetup();
			retryPendingPosts();
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			contractTracker.onInstanceState(client.getTopLevelWorldView().isInstance(), client.getTickCount());
		}

		int tick = client.getTickCount();
		if (thresholdCheckPending && tick - loginTick >= BASELINE_DELAY_TICKS && remoteConfigService.current() != null)
		{
			thresholdCheckPending = false;
			checkThresholdTasks();
		}
		List<PendingCompletion> due = pendingCompletions.stream()
			.filter(pending -> tick - pending.getTick() >= COMPLETION_DELAY_TICKS)
			.collect(Collectors.toList());
		pendingCompletions.removeAll(due);
		for (PendingCompletion pending : due)
		{
			List<TimedLine> times = recentTimes.stream()
				.filter(time -> Math.abs(time.getTick() - pending.getTick()) <= COMPLETION_DELAY_TICKS)
				.map(TickedTime::getLine)
				.collect(Collectors.toList());
			handleCompletion(pending.getTrigger(), times, pending.getKillCount());
			List<PersonalBest> bests = recentPersonalBests.stream()
				.filter(best -> Math.abs(best.getTick() - pending.getTick()) <= COMPLETION_DELAY_TICKS)
				.map(TickedPersonalBest::getBest)
				.collect(Collectors.toList());
			reportPersonalBests(pending.getTrigger(), bests);
		}
		recentTimes.removeIf(time -> tick - time.getTick() > RECENT_TIME_MAX_AGE_TICKS);
		recentPersonalBests.removeIf(best -> tick - best.getTick() > RECENT_TIME_MAX_AGE_TICKS);
		reportActivity(tick);
	}

	@Value
	private static class PendingCompletion
	{
		String trigger;
		int tick;
		int killCount;
	}

	@Value
	private static class TickedTime
	{
		TimedLine line;
		int tick;
	}

	@Value
	private static class TickedPersonalBest
	{
		PersonalBest best;
		int tick;
	}

	private void reportActivity(int tick)
	{
		itemNameIndex.onTick();
		collectionLogCapture.onTick(tick).ifPresent(this::onCollectionLogCaptured);
		if (!accountSettledAfterLogin(tick))
		{
			return;
		}
		reportStoredPersonalBests();
		BotTarget target = target();
		if (target == null)
		{
			return;
		}
		if (caSnapshotPending)
		{
			combatAchievementReader.readCompleted().ifPresent(completed ->
			{
				caSnapshotPending = false;
				activityReporter.reportCaSnapshot(target, account(), client.getAccountHash(), completed);
			});
		}
		if (statusCheckPending)
		{
			statusCheckPending = false;
			activityReporter.reportStatusIfChanged(target, account(), client.getAccountHash(),
				combatAchievementReader.readStatus(), collectionLogReader.read());
		}
	}

	/**
	 * Snapshots name the account, so they wait until the game has delivered the account hash and the player's name,
	 * which arrive during the first ticks after login. The pending flags stay set until then.
	 */
	private boolean accountSettledAfterLogin(int tick)
	{
		return tick - loginTick >= BASELINE_DELAY_TICKS && client.getAccountHash() != -1 && !account().isEmpty();
	}

	private void onCollectionLogCaptured(List<OwnedItem> items)
	{
		if (!canPostForClan())
		{
			collectionLogCapture.allowAgain();
			return;
		}
		BotTarget target = target();
		if (target != null)
		{
			activityReporter.reportClogSnapshot(target, account(), client.getAccountHash(), items);
		}
		checkClogTasksOnWholeLog(items);
	}

	/**
	 * Every one-time collection log task is judged on the whole log whenever the log is opened, so items owned before
	 * the plugin was installed count. Repeatable tasks are only judged per new item, since the whole log would complete
	 * them again at every opening.
	 */
	private void checkClogTasksOnWholeLog(List<OwnedItem> ownedItems)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		List<String> owned = ownedItems.stream().map(OwnedItem::getName).collect(Collectors.toList());
		List<TaskDefinition> completed = new ArrayList<>();
		Map<String, String> singleItems = new HashMap<>();
		for (TaskDefinition task : taskMatcher.byTrigger(remoteConfig, Triggers.CLOG))
		{
			if (!awaitsWholeLogJudgement(task))
			{
				continue;
			}
			Set<String> progress = new HashSet<>(progressStore.loggedItems(task.getId()));
			progress.addAll(clogTaskEvaluator.ownedOf(task, owned));
			if (clogTaskEvaluator.isComplete(task, progress))
			{
				completed.add(task);
				if (progress.size() == 1)
				{
					singleItems.put(task.getId(), progress.iterator().next());
				}
				progressStore.saveLoggedItems(task.getId(), Set.of());
			}
			else
			{
				progressStore.saveLoggedItems(task.getId(), progress);
			}
		}
		if (!completed.isEmpty())
		{
			postClogTasks(completed, singleItems);
		}
	}

	private boolean awaitsWholeLogJudgement(TaskDefinition task)
	{
		return task.isOnce() && notYetPosted(task);
	}

	/**
	 * A task completed by a single item names that item, like a task completed by a new item in chat does.
	 */
	private void postClogTasks(List<TaskDefinition> tasks, Map<String, String> singleItems)
	{
		Instant completedAt = Instant.now();
		String account = account();
		List<PostPayload> posts = new ArrayList<>();
		for (TaskDefinition task : tasks)
		{
			progressStore.markCompleted(task.getId());
			String item = singleItems.get(task.getId());
			if (item == null)
			{
				posts.add(messageFormatter.build(account, task, List.of(), completedAt));
			}
			else
			{
				posts.add(messageFormatter.buildItemTask(account, task, item, null, completedAt));
			}
		}
		postWithScreenshot(botTarget(), posts);
	}

	private void reportStoredPersonalBests()
	{
		BotTarget target = target();
		if (!pbSnapshotDue(target))
		{
			return;
		}
		pbSnapshotPending = false;
		activityReporter.reportPbSnapshot(target, account(), client.getAccountHash(), personalBestReader.read());
	}

	// RuneLite's stored personal bests live in the game profile, which is only known once RuneLite has resolved it
	private boolean pbSnapshotDue(BotTarget target)
	{
		return target != null && pbSnapshotPending && configManager.getRSProfileKey() != null;
	}

	/**
	 * Null when nothing may be posted: no clan config, or posting not allowed.
	 */
	private BotTarget target()
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (remoteConfig == null || !canPost())
		{
			return null;
		}
		return botTarget();
	}

	// Hub rules: posts may only go to an address the player set, never one read from the clan config
	private BotTarget botTarget()
	{
		return new BotTarget(botUrl(), clanToken());
	}

	private void reportCollectionLogItem(String item)
	{
		BotTarget target = target();
		if (target == null)
		{
			return;
		}
		activityReporter.reportClog(target, account(), item, itemId(item), 1, client.getVarpValue(VarPlayerID.COLLECTION_COUNT));
	}

	private void reportCombatAchievement(CombatAchievement achievement)
	{
		BotTarget target = target();
		if (target == null)
		{
			return;
		}
		activityReporter.reportCa(target, account(), achievement.getName(), achievement.getId(), achievement.getTier());
	}

	private int itemId(String name)
	{
		OptionalInt indexed = itemNameIndex.idOf(name);
		if (indexed.isPresent())
		{
			return indexed.getAsInt();
		}
		return itemManager.search(name).stream()
			.filter(price -> Names.same(price.getName(), name))
			.mapToInt(ItemPrice::getId)
			.findFirst()
			.orElse(-1);
	}

	/**
	 * Raids: the game keeps a PB per scale, so a new record is reported as such and the current best once per raid
	 * and login, so the board can pick up times set before the plugin was installed. Bosses: the game keeps one PB
	 * across scales, so the plugin reports every kill faster than its own memory for that scale, and the game's PB
	 * once per account with the scale left open.
	 */
	private void reportPersonalBests(String trigger, List<PersonalBest> bests)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (personalBestsOff(remoteConfig, bests))
		{
			return;
		}
		String boss = bossName(trigger);
		if (teamCannotBeToldFromBystanders(trigger))
		{
			log.debug("Personal best at {} not posted: not in an instance, so bystanders cannot be told from the team", boss);
			return;
		}
		List<String> party = soloByTheGamesCount() ? List.of(account()) : participantReader.read(trigger);
		if (partyOnlyPartlyRead(trigger, party))
		{
			log.debug("Personal best at {} not posted: team size {} but only {} found, cannot verify the clan check", boss, gamesTeamSize(trigger), party);
			return;
		}
		if (!membershipChecker.allMembers(client.getClanSettings(), remoteConfig.getClanName(), party))
		{
			log.debug("Personal best at {} not posted: only clan teams count, party was {}", boss, party);
			return;
		}
		int teamSize = teamSize(party);
		BotTarget target = botTarget();
		if (trigger.startsWith(Triggers.RAID_PREFIX))
		{
			reportRaidBests(target, boss, bests, teamSize, party);
		}
		else
		{
			reportBossBests(target, boss, bests, teamSize, party);
		}
	}

	private void reportRaidBests(BotTarget target, String boss, List<PersonalBest> bests, int teamSize, List<String> party)
	{
		boolean firstReportThisLogin = bossesReportedThisLogin.add(boss);
		for (PersonalBest best : bests)
		{
			if (best.isNewRecord())
			{
				postBest(target, boss, TimeFormat.of(best.getBestSeconds()), best.getKind(), teamSize, party, PersonalBestClaim.NEW_RECORD);
			}
			else if (firstReportThisLogin)
			{
				postBest(target, boss, TimeFormat.of(best.getBestSeconds()), best.getKind(), teamSize, party, PersonalBestClaim.CURRENT);
			}
		}
	}

	private void reportBossBests(BotTarget target, String boss, List<PersonalBest> bests, int teamSize, List<String> party)
	{
		long accountHash = client.getAccountHash();
		for (PersonalBest best : bests)
		{
			double run = best.getRunSeconds();
			if (worthPostingAtScale(accountHash, boss, best, teamSize))
			{
				scaleBestStore.saveBest(accountHash, boss, best.getKind(), teamSize, run);
				postBest(target, boss, TimeFormat.of(run), best.getKind(), teamSize, party,
					best.isNewRecord() ? PersonalBestClaim.NEW_RECORD : PersonalBestClaim.FASTEST_AT_SCALE);
			}
			if (gameBestStillUnreported(accountHash, boss, best))
			{
				scaleBestStore.markGameBestReported(accountHash, boss);
				activityReporter.send(target,
					activityFormatter.currentBossBest(account(), boss, TimeFormat.of(best.getBestSeconds()), best.getKind(), Instant.now()), null);
			}
		}
	}

	private boolean worthPostingAtScale(long accountHash, String boss, PersonalBest best, int teamSize)
	{
		return best.isNewRecord() || scaleBestStore.isFasterThanStored(accountHash, boss, best.getKind(), teamSize, best.getRunSeconds());
	}

	private boolean gameBestStillUnreported(long accountHash, String boss, PersonalBest best)
	{
		return !best.isNewRecord() && !scaleBestStore.isGameBestReported(accountHash, boss);
	}

	private void postBest(BotTarget target, String boss, String time, TimedLine.Kind kind, int teamSize, List<String> party,
		PersonalBestClaim claim)
	{
		PostPayload payload = activityFormatter.personalBest(account(), boss, time, kind, teamSize, party, claim, Instant.now());
		if (claim == PersonalBestClaim.CURRENT)
		{
			activityReporter.send(target, payload, null);
			return;
		}
		screenshotService.capture(png -> activityReporter.send(target, payload, png));
	}

	/**
	 * Raids read their party list. Other bosses only count inside an instance, where everyone present fought; on an
	 * open world nobody can tell the team from bystanders, so those kills are submitted by hand (decided with the
	 * PB board owner, 2026-10-18). The Nightmare prints its own team size, so a solo kill there is certain whoever
	 * else stands in the arena.
	 */
	private boolean teamCannotBeToldFromBystanders(String trigger)
	{
		return outsideInstance(trigger) && !soloByTheGamesCount();
	}

	private boolean outsideInstance(String trigger)
	{
		return !trigger.startsWith(Triggers.RAID_PREFIX) && !client.getTopLevelWorldView().isInstance();
	}

	private boolean soloByTheGamesCount()
	{
		return knowsTeamSizeFromChat() && lastRaidTeamSize == 1;
	}

	private boolean personalBestsOff(RemoteConfig remoteConfig, List<PersonalBest> bests)
	{
		return bests.isEmpty() || personalBestsOff(remoteConfig);
	}

	private boolean personalBestsOff(RemoteConfig remoteConfig)
	{
		return remoteConfig == null || !canPost();
	}

	// Chambers of Xeric and the Nightmare print their team size, which beats counting players in the scene
	private int teamSize(List<String> party)
	{
		return knowsTeamSizeFromChat() ? lastRaidTeamSize : party.size();
	}

	private boolean knowsTeamSizeFromChat()
	{
		return client.getTickCount() - lastRaidTeamSizeTick <= TEAM_SIZE_MAX_AGE_TICKS;
	}

	private boolean knowsCoxTeamSize(String trigger)
	{
		return isChambersOfXeric(trigger) && knowsTeamSizeFromChat();
	}

	/**
	 * The party list can hold fewer names than the game's team size, Chambers of Xeric's side panel and the
	 * Fractured Archive's name slots alike, and then the missing players cannot be checked against the clan.
	 */
	private boolean partyOnlyPartlyRead(String trigger, List<String> party)
	{
		return party.size() < gamesTeamSize(trigger);
	}

	/** The team size the game itself states, from the chat line or the party slots, or 0 when it states none. */
	private int gamesTeamSize(String trigger)
	{
		return knowsCoxTeamSize(trigger) ? lastRaidTeamSize : participantReader.gamesTeamSize(trigger);
	}

	private static String bossName(String trigger)
	{
		int colon = trigger.indexOf(':');
		return colon < 0 ? trigger : trigger.substring(colon + 1);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.PLAYER || PVP_LOOT_CHEST.equals(event.getName()))
		{
			log.debug("Ignoring PvP loot from {}", event.getName());
			return;
		}
		handleLoot(event.getName(), event.getItems());
	}

	private CompletableFuture<Void> refreshRemoteConfig()
	{
		return remoteConfigService.refresh(config.enabled() ? config.configUrl() : null);
	}

	private static boolean isChambersOfXeric(String trigger)
	{
		return trigger.startsWith(Triggers.raid("Chambers of Xeric"));
	}

	private void handleCompletion(String trigger, List<TimedLine> times, int killCount)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		Player localPlayer = client.getLocalPlayer();
		if (!canPost() || remoteConfig == null || localPlayer == null)
		{
			log.debug("Ignoring {}: clan config loaded={}, local player={}", trigger, remoteConfig != null, localPlayer != null);
			return;
		}

		List<String> participants = participantReader.read(trigger);
		if (partyOnlyPartlyRead(trigger, participants))
		{
			log.info("Not posted {}: team size {} but only {} found, cannot verify the clan check", trigger, gamesTeamSize(trigger), participants);
			return;
		}
		int teamSize = knowsCoxTeamSize(trigger) ? lastRaidTeamSize : participants.size();
		List<TaskDefinition> tasks = taskMatcher.match(remoteConfig, trigger, participants.size()).stream()
			.filter(this::notYetPosted)
			.filter(task -> timedTaskFilter.accepts(task, times, teamSize, killCount))
			.collect(Collectors.toList());
		if (tasks.isEmpty())
		{
			boolean anyCandidate = taskMatcher.byTrigger(remoteConfig, trigger).stream().anyMatch(this::notYetPosted);
			if (anyCandidate)
			{
				log.info("Not posted {}: {} players, team size {}, kill count {} and times {} did not meet any task", trigger, participants.size(), teamSize, killCount, times);
			}
			return;
		}

		postSoloAndClanOnly(remoteConfig, trigger, tasks, participants);
	}

	private void postSoloAndClanOnly(RemoteConfig remoteConfig, String trigger, List<TaskDefinition> tasks, List<String> participants)
	{
		ClanSettings clan = client.getClanSettings();
		if (!membershipChecker.isClan(clan, remoteConfig.getClanName()))
		{
			log.info("Not posted {}: clan settings missing or not in the configured clan", trigger);
			return;
		}
		List<TaskDefinition> soloTasks = tasks.stream().filter(task -> !task.isClanOnly()).collect(Collectors.toList());
		List<TaskDefinition> clanOnlyTasks = tasks.stream().filter(TaskDefinition::isClanOnly).collect(Collectors.toList());
		List<String> party = clanPartyToName(trigger, clan, remoteConfig, participants);
		if (!soloTasks.isEmpty())
		{
			postTasks(soloTasks, party);
		}
		if (clanOnlyTasks.isEmpty())
		{
			return;
		}
		if (membershipChecker.allMembers(clan, remoteConfig.getClanName(), participants))
		{
			postTasks(clanOnlyTasks, party);
		}
		else
		{
			log.info("Not posted {}: outsider among {}", trigger, participants);
		}
	}

	/**
	 * The clan members in the team, for the post. Outside an instance the players around are bystanders, not a
	 * team, so nobody is named there.
	 */
	private List<String> clanPartyToName(String trigger, ClanSettings clan, RemoteConfig remoteConfig, List<String> participants)
	{
		if (outsideInstance(trigger))
		{
			return List.of();
		}
		return membershipChecker.membersAmong(clan, remoteConfig.getClanName(), participants);
	}

	/**
	 * Equipment, experience and collection log count conditions can already be met when the plugin first sees
	 * an account. The first check for each task only records that state, and only later changes get posted.
	 * The baseline is per account, as an alt without the item says nothing about the main.
	 */
	private void checkThresholdTasks()
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (!canPostForClan())
		{
			return;
		}

		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			thresholdCheckPending = true;
			return;
		}
		Set<String> worn = itemNames(client.getItemContainer(InventoryID.WORN));
		Set<String> held = new HashSet<>(worn);
		held.addAll(itemNames(client.getItemContainer(InventoryID.INV)));
		int clogCount = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		log.debug("Threshold check: {} collection log slots", clogCount);
		List<TaskDefinition> reached = new ArrayList<>();
		for (TaskDefinition task : remoteConfig.getTasks())
		{
			Optional<Boolean> satisfied = thresholdSatisfied(task, worn, held, clogCount);
			if (satisfied.isEmpty() || progressStore.isCompleted(task.getId()))
			{
				continue;
			}
			boolean firstCheck = !progressStore.isBaselined(task.getId(), accountHash);
			if (firstCheck)
			{
				progressStore.markBaselined(task.getId(), accountHash);
			}
			if (satisfied.get())
			{
				progressStore.markCompleted(task.getId());
				if (!firstCheck)
				{
					reached.add(task);
				}
			}
		}
		if (!reached.isEmpty())
		{
			postTasks(reached);
		}
	}

	private Optional<Boolean> thresholdSatisfied(TaskDefinition task, Set<String> worn, Set<String> held, int clogCount)
	{
		String trigger = task.getTrigger();
		if (trigger == null)
		{
			return Optional.empty();
		}
		if (Names.same(trigger, Triggers.EQUIP))
		{
			return Optional.of(thresholdTaskEvaluator.isEquipped(task, worn));
		}
		if (Names.same(trigger, Triggers.CLOG_COUNT))
		{
			return clogCount > 0 ? Optional.of(thresholdTaskEvaluator.hasCount(task, clogCount)) : Optional.empty();
		}
		if (Names.same(trigger, Triggers.HOLD))
		{
			return Optional.of(thresholdTaskEvaluator.isEquipped(task, held));
		}
		if (trigger.toUpperCase().startsWith(Triggers.XP_PREFIX))
		{
			String skillName = trigger.substring(Triggers.XP_PREFIX.length());
			return Arrays.stream(Skill.values())
				.filter(skill -> Names.same(skill.getName(), skillName))
				.findFirst()
				.map(client::getSkillExperience)
				.filter(experience -> experience > 0)
				.map(experience -> thresholdTaskEvaluator.hasExperience(task, experience));
		}
		return Optional.empty();
	}

	private Set<String> itemNames(ItemContainer container)
	{
		if (container == null)
		{
			return Set.of();
		}
		return Arrays.stream(container.getItems())
			.filter(item -> item.getId() > 0)
			.map(item -> itemManager.getItemComposition(item.getId()).getName())
			.collect(Collectors.toSet());
	}

	private void handleDelve(int level)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (!canPostForClan())
		{
			return;
		}
		List<TaskDefinition> tasks = taskMatcher.byTrigger(remoteConfig, Triggers.DELVE).stream()
			.filter(this::notYetPosted)
			.filter(task -> level >= task.getMinLevel())
			.collect(Collectors.toList());
		if (!tasks.isEmpty())
		{
			postTasks(tasks);
		}
	}

	private void handlePersonalTrigger(String trigger)
	{
		handlePersonalTrigger(List.of(trigger));
	}

	/**
	 * Several triggers describe the same event, such as a combat achievement's name and id, so a task listing
	 * both is still posted once.
	 */
	private void handlePersonalTrigger(List<String> triggers)
	{
		String trigger = triggers.get(0);
		RemoteConfig remoteConfig = remoteConfigService.current();
		Player localPlayer = client.getLocalPlayer();
		if (!canPost() || remoteConfig == null || localPlayer == null)
		{
			log.debug("Ignoring {}: clan config loaded={}, local player={}", trigger, remoteConfig != null, localPlayer != null);
			return;
		}

		List<String> participants = participantReader.read(trigger);
		List<TaskDefinition> tasks = taskMatcher.match(remoteConfig, triggers, participants.size()).stream()
			.filter(this::notYetPosted)
			.collect(Collectors.toList());
		if (!tasks.isEmpty())
		{
			postSoloAndClanOnly(remoteConfig, trigger, tasks, participants);
		}
	}

	private void handleCollectionLog(String item)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (!canPostForClan())
		{
			log.info("Ignoring collection log item {}: posting disabled, clan config missing or not in the configured clan", item);
			return;
		}

		boolean matchedAnyTask = false;
		List<TaskDefinition> completed = new ArrayList<>();
		for (TaskDefinition task : taskMatcher.byTrigger(remoteConfig, Triggers.CLOG))
		{
			if (!notYetPosted(task))
			{
				continue;
			}
			Optional<Set<String>> progressAfter = clogTaskEvaluator.progressAfter(task, progressStore.loggedItems(task.getId()), item);
			matchedAnyTask = matchedAnyTask || progressAfter.isPresent();
			progressAfter.ifPresent(progress ->
			{
				if (clogTaskEvaluator.isComplete(task, progress))
				{
					completed.add(task);
					progressStore.saveLoggedItems(task.getId(), Set.of());
				}
				else
				{
					progressStore.saveLoggedItems(task.getId(), progress);
				}
			});
		}
		if (completed.isEmpty())
		{
			if (!matchedAnyTask)
			{
				log.info("Collection log item {} is not part of any clan task", item);
			}
			return;
		}
		Instant completedAt = Instant.now();
		String account = account();
		List<PostPayload> posts = new ArrayList<>();
		for (TaskDefinition task : completed)
		{
			if (task.isOnce())
			{
				progressStore.markCompleted(task.getId());
			}
			posts.add(messageFormatter.buildItemTask(account, task, item, null, completedAt));
		}
		postWithScreenshot(botTarget(), posts);
	}

	private void postTasks(List<TaskDefinition> tasks)
	{
		postTasks(tasks, List.of());
	}

	private void postTasks(List<TaskDefinition> tasks, List<String> party)
	{
		Instant completedAt = Instant.now();
		String account = account();
		List<PostPayload> posts = new ArrayList<>();
		for (TaskDefinition task : tasks)
		{
			if (task.isOnce())
			{
				progressStore.markCompleted(task.getId());
			}
			posts.add(messageFormatter.build(account, task, party, completedAt));
		}
		postWithScreenshot(botTarget(), posts);
	}

	private boolean notYetPosted(TaskDefinition task)
	{
		return !task.isOnce() || !progressStore.isCompleted(task.getId());
	}

	private void warnIfCollectionLogChatDisabled()
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (!config.enabled() || remoteConfig == null)
		{
			return;
		}
		clogSettingCheckPending = false;
		int setting = client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM);
		log.debug("Collection log notification setting is {}", setting);
		if ((setting & COLLECTION_LOG_CHAT_BIT) == 0 && !taskMatcher.byTrigger(remoteConfig, Triggers.CLOG).isEmpty())
		{
			warnInChat("Slay Clan: turn on the game setting 'Collection log - New addition notification', otherwise collection log tasks are not detected.");
		}
	}

	private void handleLoot(String source, Collection<ItemStack> items)
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		Player localPlayer = client.getLocalPlayer();
		if (!canPost() || remoteConfig == null || localPlayer == null)
		{
			return;
		}

		List<TaskDefinition> dropTasks = taskMatcher.byTrigger(remoteConfig, Triggers.DROP);
		List<TaskDefinition> lootTasks = taskMatcher.byTrigger(remoteConfig, Triggers.LOOT).stream()
			.filter(this::notYetPosted)
			.collect(Collectors.toList());
		if ((dropTasks.isEmpty() && lootTasks.isEmpty())
			|| !membershipChecker.isClan(client.getClanSettings(), remoteConfig.getClanName()))
		{
			log.debug("Ignoring loot from {}: no loot task or not in the configured clan", source);
			return;
		}

		List<PricedItem> pricedItems = items.stream().map(this::price).collect(Collectors.toList());
		Instant completedAt = Instant.now();
		String account = account();
		List<PostPayload> posts = new ArrayList<>();
		for (TaskDefinition task : dropTasks)
		{
			for (ValuableDrop drop : dropEvaluator.evaluate(pricedItems, task))
			{
				posts.add(messageFormatter.buildDrop(account, drop, source, completedAt));
			}
		}
		for (TaskDefinition task : lootTasks)
		{
			for (PricedItem item : pricedItems)
			{
				if (!lootTaskEvaluator.matches(task, source, item.getName()))
				{
					continue;
				}
				posts.add(messageFormatter.buildItemTask(account, task, item.getName(), source, completedAt));
				if (task.isOnce())
				{
					progressStore.markCompleted(task.getId());
					break;
				}
			}
		}
		if (!posts.isEmpty())
		{
			postWithScreenshot(botTarget(), posts);
		}
	}

	private PricedItem price(ItemStack item)
	{
		ItemComposition dropped = itemManager.getItemComposition(item.getId());
		int itemId = itemManager.canonicalize(item.getId());
		return new PricedItem(
			itemManager.getItemComposition(itemId).getName(),
			item.getQuantity(),
			itemManager.getItemPrice(itemId),
			dropped.isStackable());
	}

	private void postWithScreenshot(BotTarget target, List<PostPayload> posts)
	{
		screenshotService.capture(png -> posts.forEach(payload -> activityReporter.send(target, payload, png)));
	}

	/**
	 * Only one retry round runs at a time, otherwise two rounds started close together would both deliver the
	 * same post.
	 */
	private void retryPendingPosts()
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (!canPost() || remoteConfig == null || !retryInProgress.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			activityReporter.retry(this::botTarget, pendingPostStore.load(Instant.now()))
				.whenComplete((ignored, error) -> retryInProgress.set(false));
		}
		catch (RuntimeException e)
		{
			log.warn("Retrying pending posts failed", e);
			retryInProgress.set(false);
		}
	}

	private boolean canPostForClan()
	{
		RemoteConfig remoteConfig = remoteConfigService.current();
		return canPost() && remoteConfig != null && client.getLocalPlayer() != null
			&& membershipChecker.isClan(client.getClanSettings(), remoteConfig.getClanName());
	}

	/**
	 * Posts need the member's clan token so the bot knows whose they are, and a profile that syncs to a RuneLite
	 * account so that one-time tasks are remembered across computers and shared with alts.
	 */
	private boolean canPost()
	{
		boolean canPost = config.enabled() && hasSecretSafeBotUrl() && !clanToken().isEmpty() && profileSyncsToAccount();
		if (!canPost && !postingBlockedLogged)
		{
			postingBlockedLogged = true;
			log.info("Posting blocked: enabled={}, bot URL usable={}, clan token set={}, profile synced={}",
				config.enabled(), hasSecretSafeBotUrl(), !clanToken().isEmpty(), profileSyncsToAccount());
		}
		return canPost;
	}

	// Posts carry the member's token
	private boolean hasSecretSafeBotUrl()
	{
		return SecretSafeUrl.accepts(botUrl());
	}

	private String botUrl()
	{
		return trimmed(config.botUrl());
	}

	private static String trimmed(String setting)
	{
		return setting == null ? "" : setting.trim();
	}

	// A synced profile needs a RuneLite account login, and it is the sync that carries the task memory along
	private boolean profileSyncsToAccount()
	{
		ConfigProfile profile = configManager.getProfile();
		return profile != null && profile.isSync();
	}

	private String clanToken()
	{
		return trimmed(config.clanToken());
	}

	private String account()
	{
		Player localPlayer = client.getLocalPlayer();
		return localPlayer == null || localPlayer.getName() == null ? "" : Text.sanitize(localPlayer.getName());
	}

	private void warnAboutSetup()
	{
		setupWarningPending = false;
		if (!config.enabled())
		{
			return;
		}
		warnIfBotUrlUnusable();
		if (clanToken().isEmpty())
		{
			warnInChat("Slay Clan: paste your clan token into the plugin settings, otherwise nothing is posted. Run /plugin token in the clan Discord to get one.");
		}
		if (!profileSyncsToAccount())
		{
			warnInChat("Slay Clan: log in with a RuneLite account and turn on Sync for this profile in the profile panel, otherwise nothing is posted.");
		}
		int dropped = pendingPostStore.takeDroppedCount();
		if (dropped > 0)
		{
			warnInChat("Slay Clan: " + dropped + " post(s) could not be delivered to the clan bot and were dropped. Submit those tasks manually.");
		}
		if (config.configUrl() == null || config.configUrl().trim().isEmpty())
		{
			warnInChat("Slay Clan: no clan config URL is set. Paste the URL from the clan Discord into the plugin settings.");
			return;
		}
		if (remoteConfigService.lastRefreshFailed())
		{
			warnInChat("Slay Clan: the clan config could not be loaded. Check the config URL in the plugin settings, or ask the clan staff if the config was changed.");
		}
		RemoteConfig remoteConfig = remoteConfigService.current();
		if (remoteConfig == null)
		{
			return;
		}
		boolean needsLootTracker = !taskMatcher.byTrigger(remoteConfig, Triggers.LOOT).isEmpty()
			|| !taskMatcher.byTrigger(remoteConfig, Triggers.DROP).isEmpty();
		boolean lootTrackerEnabled = pluginManager.getPlugins().stream()
			.anyMatch(plugin -> plugin instanceof LootTrackerPlugin && pluginManager.isPluginEnabled(plugin));
		if (needsLootTracker && !lootTrackerEnabled)
		{
			warnInChat("Slay Clan: enable the Loot Tracker plugin, otherwise drops and loot tasks are not detected.");
		}
	}

	private void warnInChat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", ColorUtil.wrapWithColorTag(message, Color.RED), null);
	}

	// The bot answers on an HTTP thread, and the chat is only written from the client thread
	private void tell(BotReply reply)
	{
		if (reply.getMessage() == null || blockerAlreadySaid(reply))
		{
			return;
		}
		clientThread.invoke(() -> warnInChat("Slay Clan: " + reply.getMessage()));
	}

	private boolean blockerAlreadySaid(BotReply reply)
	{
		return reply.isBlocked() && !blockersSaid.add(reply.getBlocker());
	}

	// The setting was replaced by the clan token; the old value would otherwise sit in every profile for good
	private void forgetTheRetiredDiscordNameSetting()
	{
		configManager.unsetConfiguration(SlayClanConfig.GROUP, RETIRED_DISCORD_NAME_KEY);
	}

	@Provides
	SlayClanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayClanConfig.class);
	}
}
