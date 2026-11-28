package com.slayclan;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import com.slayclan.activity.CollectionLogCapture;
import com.slayclan.activity.CombatAchievementReader;
import com.slayclan.config.RemoteConfig;
import com.slayclan.config.RemoteConfigService;
import com.slayclan.config.TaskDefinition;
import com.slayclan.discord.BotClient;
import com.slayclan.discord.BotReply;
import com.slayclan.discord.BotTarget;
import com.slayclan.discord.KeptScreenshots;
import com.slayclan.discord.PendingPost;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.ScreenshotService;
import com.slayclan.discord.PostPayload;
import com.slayclan.PluginVersion;
import com.slayclan.progress.TaskProgressStore;
import com.slayclan.raid.RaidParticipantReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.runelite.api.ChatMessageType;
import net.runelite.api.WorldType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptEvent;
import net.runelite.api.StructComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.http.api.loottracker.LootRecordType;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

/**
 * Drives the plugin through game events and checks what reaches Discord. Memory is faked with sets so the
 * per-account baseline and one-time logic run for real.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SlayClanPluginFlowTest
{
	private static final String API = "https://bot.example/api/v1/posts";
	private static final String TOKEN = "sc_test";
	private static final BotTarget TARGET = new BotTarget(API, TOKEN);
	private static final String ME = "Clanmate";
	private static final String OUTSIDER = "Stranger";
	private static final int HORN_OF_BRAN = 32000;
	private static final byte[] PLAIN_SCREENSHOT = {1};

	@Mock
	private Client client;
	@Mock
	private SlayClanConfig config;
	@Mock
	private ScheduledExecutorService executor;
	@Mock
	private RemoteConfigService remoteConfigService;
	@Mock
	private RaidParticipantReader participantReader;
	@Mock
	private ScreenshotService screenshotService;
	@Mock
	private BotClient botClient;
	@Mock
	private ItemManager itemManager;
	@Mock
	private TaskProgressStore progressStore;
	@Mock
	private PendingPostStore pendingPostStore;
	@Mock
	private KeptScreenshots keptScreenshots;
	@Mock
	private PluginManager pluginManager;
	@Mock
	private ConfigManager configManager;
	@Mock
	private ClientThread clientThread;
	@Mock
	private ClanSettings clan;
	@Mock
	private Player localPlayer;
	@Mock
	private WorldView worldView;

	@Spy
	private Gson gson = new Gson();

	@InjectMocks
	private SlayClanPlugin plugin;

	private final RemoteConfig remoteConfig = new RemoteConfig();
	private final ListAppender<ILoggingEvent> pluginLog = new ListAppender<>();
	private final Set<String> completed = new HashSet<>();
	private final Set<String> judged = new HashSet<>();
	private int tick = 100;

	@Before
	public void setUp()
	{
		pluginLog.start();
		pluginLogger().addAppender(pluginLog);
		remoteConfig.setClanName("Slay Clan");
		when(remoteConfigService.current()).thenReturn(remoteConfig);
		when(remoteConfigService.refresh(any())).thenReturn(CompletableFuture.completedFuture(null));
		when(config.enabled()).thenReturn(true);
		when(config.configUrl()).thenReturn("https://example.test/config.json");
		when(config.botUrl()).thenReturn(API);
		when(config.clanToken()).thenReturn(TOKEN);
		ConfigProfile syncedProfile = mock(ConfigProfile.class);
		when(syncedProfile.isSync()).thenReturn(true);
		when(configManager.getProfile()).thenReturn(syncedProfile);
		when(pluginManager.getPlugins()).thenReturn(List.of());
		when(client.getLocalPlayer()).thenReturn(localPlayer);
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		when(localPlayer.getName()).thenReturn(ME);
		when(client.getTickCount()).thenAnswer(invocation -> tick);
		when(client.getAccountHash()).thenReturn(1L);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		Map<String, String> memory = new HashMap<>();
		when(configManager.getConfiguration(anyString(), anyString()))
			.thenAnswer(invocation -> memory.get(invocation.getArgument(0) + "." + invocation.getArgument(1)));
		doAnswer(invocation -> memory.put(invocation.getArgument(0) + "." + invocation.getArgument(1), invocation.getArgument(2)))
			.when(configManager).setConfiguration(anyString(), anyString(), anyString());
		when(worldView.isInstance()).thenReturn(true);
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		when(client.getClanSettings()).thenReturn(clan);
		EnumComposition noTasks = mock(EnumComposition.class);
		when(noTasks.getIntVals()).thenReturn(new int[0]);
		when(noTasks.getKeys()).thenReturn(new int[0]);
		when(client.getEnum(anyInt())).thenReturn(noTasks);
		when(clan.getName()).thenReturn("Slay Clan");
		when(clan.findMember(ME)).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME));
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(BotReply.recorded(null)));
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(BotReply.recorded(null)));
		doAnswer(invocation ->
		{
			Consumer<byte[]> onCaptured = invocation.getArgument(0);
			onCaptured.accept(PLAIN_SCREENSHOT);
			return null;
		}).when(screenshotService).capture(any());
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).execute(any(Runnable.class));
		when(progressStore.isCompleted(anyString())).thenAnswer(invocation -> completed.contains(invocation.<String>getArgument(0)));
		doAnswer(invocation -> completed.add(invocation.getArgument(0))).when(progressStore).markCompleted(anyString());
		doAnswer(invocation -> completed.remove(invocation.<String>getArgument(0))).when(progressStore).clearCompleted(anyString());
		when(progressStore.isJudgedFromGameData(anyString(), anyLong()))
			.thenAnswer(invocation -> judged.contains(judgementKey(invocation.getArgument(0), invocation.getArgument(1))));
		doAnswer(invocation -> judged.add(judgementKey(invocation.getArgument(0), invocation.getArgument(1))))
			.when(progressStore).markJudgedFromGameData(anyString(), anyLong());
		plugin.startUp();
	}

	@After
	public void tearDown()
	{
		pluginLogger().detachAppender(pluginLog);
		pluginLog.stop();
		plugin.shutDown();
	}

	@Test
	public void postsRaidWhenTimeAndKillCountArriveOnDifferentTicks()
	{
		tasks(task("lone-raider", "RAID:Chambers of Xeric*", "16:00", 1, false));

		chat("Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>15:00.00</col> Personal best: 14:00");
		tick++;
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(2);

		assertEquals(List.of("Lone Raider"), postedTasks());
	}

	@Test
	public void skipsSpeedTaskOverTheLimit()
	{
		tasks(task("lone-raider", "RAID:Chambers of Xeric*", "16:00", 1, false));

		chat("Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>16:00.20</col> Personal best: 14:00");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		assertEquals(List.of(), postedTasks());
	}

	@Test
	public void skipsCoxWhenFewerPlayersAreFoundThanTheTeamSize()
	{
		tasks(task("clan-raid", "RAID:Chambers of Xeric*", null, 0, true));
		when(participantReader.read(anyString())).thenReturn(List.of(ME));

		chat("Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>30:00.00</col> Personal best: 14:00");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		assertTrue(postedTasksDropsAndBests().isEmpty());
	}

	@Test
	public void raidTaskPostNamesTheClanMembersInTheTeam()
	{
		tasks(task("clan-raid", "RAID:*", null, 0, true));
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Third")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy", "Third"));

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		assertEquals("Clanmate, Raidbuddy, Third", payload.getValue().getFields().get("Party"));
	}

	@Test
	public void bossTaskOutsideAnInstanceNamesNobody()
	{
		tasks(task("any-kill", "KILL:*", null, 0, false));
		when(worldView.isInstance()).thenReturn(false);
		when(clan.findMember("Bystander")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Bystander"));

		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		assertEquals(List.of("Any Kill"), postedTasks());
		assertNull(postedField("Party"));
	}

	@Test
	public void clanOnlyTaskNeedsEveryoneInTheClanWhileSoloTaskDoesNot()
	{
		tasks(task("clan-raid", "RAID:*", null, 0, true), task("any-raid", "RAID:*", null, 0, false));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		assertEquals(List.of("Any Raid"), postedTasks());
	}

	@Test
	public void postsOneTimeTaskOnlyOnce()
	{
		TaskDefinition task = task("cave-runner", "KILL:TzTok-Jad", null, 0, false);
		task.setOnce(true);
		tasks(task);

		chat("Your TzTok-Jad kill count is: <col=ff0000>1</col>.");
		ticks(3);
		chat("Your TzTok-Jad kill count is: <col=ff0000>2</col>.");
		ticks(3);

		assertEquals(List.of("Cave Runner"), postedTasks());
	}

	@Test
	public void onlyLoginAfterLoggingInOrHoppingRefreshesTheConfig()
	{
		gameState(GameState.LOGGING_IN);
		gameState(GameState.LOADING);
		gameState(GameState.LOGGED_IN);
		gameState(GameState.LOADING);
		gameState(GameState.LOGGED_IN);
		gameState(GameState.HOPPING);
		gameState(GameState.LOGGED_IN);

		verify(remoteConfigService, times(2)).refresh(any());
	}

	@Test
	public void capeAlreadyWornAtLoginIsPosted()
	{
		tasks(withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape"));
		wearing("Quest point cape");

		login();
		ticks(6);

		assertEquals(List.of("Quest Completed"), postedTasks());
		assertEquals(Set.of("quest-completed"), completed);
	}

	@Test
	public void postsEquipmentWornAfterTheFirstCheck()
	{
		tasks(withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape"));
		wearing();
		login();
		ticks(6);

		wearing("Quest point cape");
		plugin.onStatChanged(null);
		ticks(1);

		assertEquals(List.of("Quest Completed"), postedTasks());
	}

	@Test
	public void keepsUndeliveredPostForRetry()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)));

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(pendingPostStore).add(any(), any(), anyBoolean());
	}

	@Test
	public void retriesThePostsKeptForTheAccountThatIsLoggedIn()
	{
		tasks();

		login();
		ticks(16);

		verify(pendingPostStore, atLeastOnce()).load(eq(1L), any());
		verify(pendingPostStore, never()).load(eq(2L), any());
	}

	@Test
	public void runsOneRetryRoundAtATime()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Old post"));
		when(pendingPostStore.load(anyLong(), any())).thenReturn(List.of(pending));
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(new CompletableFuture<>());

		login();
		ticks(16);
		login();
		ticks(16);

		verify(botClient, times(1)).send(eq(TARGET), resendOf(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void startsANewRetryRoundOnceTheLastOneFinished()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Old post"));
		when(pendingPostStore.load(anyLong(), any())).thenReturn(List.of(pending));
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)));

		login();
		ticks(16);
		login();
		ticks(16);

		verify(botClient, times(2)).send(eq(TARGET), resendOf(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void releasesTheRetryLockWhenLoadingThePendingPostsFails()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Old post"));
		when(pendingPostStore.load(anyLong(), any())).thenThrow(new IllegalStateException("broken")).thenReturn(List.of(pending));

		login();
		ticks(16);
		login();
		ticks(16);

		verify(botClient, times(1)).send(eq(TARGET), resendOf(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void enablingThePluginDuringLoadingStillRunsTheLoginChecks()
	{
		tasks(withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape"));
		wearing();
		when(client.getGameState()).thenReturn(GameState.LOADING);
		plugin.startUp();
		gameState(GameState.LOGGED_IN);
		ticks(6);

		wearing("Quest point cape");
		plugin.onStatChanged(null);
		ticks(1);

		assertEquals(List.of("Quest Completed"), postedTasks());
	}

	@Test
	public void postsEveryNewCollectionLogItemToTheActivityWebhook()
	{
		tasks();
		ItemPrice vorki = new ItemPrice();
		vorki.setId(21992);
		vorki.setName("Vorki");
		when(itemManager.search("Vorki")).thenReturn(List.of(vorki));

		chat("New item added to your collection log: <col=ff0000>Vorki</col>");

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) isNull());
		Map<String, String> fields = payload.getValue().getFields();
		assertEquals("Vorki", fields.get("Item"));
		assertEquals("21992", fields.get("Item id"));
	}

	@Test
	public void postsEveryCombatAchievementToTheActivityWebhook()
	{
		tasks();

		chat("CA_ID:398|Congratulations, you've completed an easy combat task: @ach_comp@Into the Den of Giants</col> (1 point).");

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) isNull());
		Map<String, String> fields = payload.getValue().getFields();
		assertEquals("398", fields.get("Task id"));
		assertEquals("Easy", fields.get("Tier"));
	}

	@Test
	public void postsAClogSetTaskWhenTheOpenedLogHoldsEnoughOfItsItems()
	{
		TaskDefinition petHoarder = withItems(task("pet-hoarder", "CLOG", null, 0, false), "Vorki", "Olmlet", "Pet snakeling");
		petHoarder.setRequired(2);
		petHoarder.setOnce(true);
		tasks(petHoarder);
		login();

		openCollectionLogWith("Olmlet", "Bandos chestplate", "Pet snakeling");
		ticks(3);

		assertEquals(List.of("Pet Hoarder"), postedTasks());
		assertTrue(completed.contains("pet-hoarder"));
	}

	@Test
	public void postsNoClogSetTaskWhenTheOpenedLogHoldsTooFewOfItsItems()
	{
		TaskDefinition petHoarder = withItems(task("pet-hoarder", "CLOG", null, 0, false), "Vorki", "Olmlet", "Pet snakeling");
		petHoarder.setRequired(2);
		petHoarder.setOnce(true);
		tasks(petHoarder);
		login();

		openCollectionLogWith("Olmlet", "Bandos chestplate");
		ticks(3);

		assertEquals(List.of(), postedTasks());
	}

	@Test
	public void keepsItemsAlreadyCountedForAClogSetTaskWhenTheOpenedLogLacksThem()
	{
		TaskDefinition petHoarder = withItems(task("pet-hoarder", "CLOG", null, 0, false), "Vorki", "Olmlet", "Pet snakeling");
		petHoarder.setRequired(3);
		petHoarder.setOnce(true);
		tasks(petHoarder);
		when(progressStore.loggedItems("pet-hoarder")).thenReturn(Set.of("Vorki"));
		login();

		openCollectionLogWith("Olmlet");
		ticks(3);

		verify(progressStore).saveLoggedItems("pet-hoarder", Set.of("Vorki", "Olmlet"));
	}

	@Test
	public void postsASingleItemClogTaskWithTheItemNamedWhenTheOpenedLogHoldsIt()
	{
		TaskDefinition fireCape = withItems(task("fire-cape", "CLOG", null, 0, false), "Fire cape");
		fireCape.setOnce(true);
		tasks(fireCape);
		login();

		openCollectionLogWith("Bandos chestplate", "Fire cape");
		ticks(3);

		assertEquals(List.of("Fire Cape"), postedTasks());
		assertEquals("Fire cape", postedField("Item"));
		assertTrue(completed.contains("fire-cape"));
	}

	@Test
	public void postsASingleItemClogTaskOnlyOnceWhenTheItemLaterArrivesInChatToo()
	{
		TaskDefinition fireCape = withItems(task("fire-cape", "CLOG", null, 0, false), "Fire cape");
		fireCape.setOnce(true);
		tasks(fireCape);
		login();

		openCollectionLogWith("Fire cape");
		ticks(3);
		chat("New item added to your collection log: <col=ff0000>Fire cape</col>");

		assertEquals(List.of("Fire Cape"), postedTasks());
	}

	@Test
	public void judgesARepeatableClogTaskOnlyPerNewItemNotOnTheOpenedLog()
	{
		TaskDefinition pets = withItems(task("new-pets", "CLOG", null, 0, false), "Vorki", "Olmlet", "Pet snakeling");
		pets.setRequired(2);
		tasks(pets);
		login();

		openCollectionLogWith("Olmlet", "Pet snakeling");
		ticks(3);

		assertEquals(List.of(), postedTasks());
	}

	@Test
	public void leavesTheCollectionLogAloneWhenPostingIsOff()
	{
		tasks();
		when(config.enabled()).thenReturn(false);
		login();

		plugin.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));

		verify(client, never()).runScript(anyInt());
	}

	@Test
	public void sendsStatusAndCaSnapshotAfterLogin()
	{
		tasks();
		EnumComposition empty = mock(EnumComposition.class);
		when(empty.getIntVals()).thenReturn(new int[0]);
		when(client.getEnum(anyInt())).thenReturn(empty);

		login();
		ticks(6);

		assertEquals(List.of("ca-snapshot", "status"), postedKinds());
	}

	@Test
	public void waitsWithStatusAndSnapshotsUntilTheGameHasSentTheAccountHash()
	{
		tasks();
		when(client.getAccountHash()).thenReturn(-1L);
		login();
		ticks(6);

		verify(botClient, never()).send(eq(TARGET), any(), (byte[]) any());

		when(client.getAccountHash()).thenReturn(1L);
		ticks(1);

		verify(botClient, times(2)).send(eq(TARGET), any(), (byte[]) any());
	}

	@Test
	public void waitsWithTheCaSnapshotUntilTheCacheHasLoadedTheTaskLists()
	{
		tasks();
		EnumComposition empty = mock(EnumComposition.class);
		when(empty.getIntVals()).thenReturn(new int[0]);
		when(client.getEnum(anyInt())).thenReturn(null);
		login();
		ticks(6);

		assertEquals(List.of("status"), postedKinds());

		when(client.getEnum(anyInt())).thenReturn(empty);
		ticks(1);

		assertEquals(List.of("status", "ca-snapshot"), postedKinds());
	}

	@Test
	public void sendsStatusAgainOnlyWhenACollectionLogOrCombatAchievementValueChanges()
	{
		tasks();
		login();
		ticks(6);
		assertEquals(1, postedCount("status"));

		plugin.onVarbitChanged(varbitChanged(VarbitID.CHAT_FILTER_ASSISTSYSTEM, -1));
		ticks(1);
		assertEquals(1, postedCount("status"));

		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY)).thenReturn(1);
		plugin.onVarbitChanged(varbitChanged(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY, -1));
		ticks(1);
		assertEquals(2, postedCount("status"));
	}

	@Test
	public void postsANewPersonalBestWithScreenshotToThePbWebhook()
	{
		tasks();

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		Map<String, String> fields = payload.getValue().getFields();
		assertEquals("Zulrah", fields.get("Boss"));
		assertEquals("0:55", fields.get("Time"));
		assertEquals("yes", fields.get("New"));
	}

	@Test
	public void postsTheFirstKillAtAScaleButNeverTheGamesStoredBestForTheBoss()
	{
		tasks();
		login();

		chat("Fight duration: <col=ff0000>1:10</col>. Personal best: 0:55");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		List<PostPayload> bests = posted("pb");
		assertEquals(1, bests.size());
		Map<String, String> fastest = bests.get(0).getFields();
		assertEquals("no", fastest.get("New"));
		assertEquals("1:10", fastest.get("Time"));
		assertEquals("1", fastest.get("Team size"));
		assertEquals("no", fastest.get("Stored best"));

		chat("Fight duration: <col=ff0000>1:12</col>. Personal best: 0:55");
		chat("Your Zulrah kill count is: <col=ff0000>11</col>.");
		ticks(3);
		login();
		chat("Fight duration: <col=ff0000>1:12</col>. Personal best: 0:55");
		chat("Your Zulrah kill count is: <col=ff0000>12</col>.");
		ticks(3);

		assertEquals(1, postedCount("pb"));
	}

	@Test
	public void postsNothingForARaidWithoutANewRecordEvenAsTheFirstRaidAfterLogin()
	{
		tasks();
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy"));
		login();

		chat("Tombs of Amascut: Expert Mode challenge completion time: <col=ff0000>28:38</col>. Personal best: 26:43");
		chat("Tombs of Amascut total completion time: <col=ff0000>31:30.00</col>. Personal best: 29:57.60");
		chat("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>10</col>.");
		ticks(3);

		assertEquals(0, postedCount("pb"));
	}

	@Test
	public void reportsAFasterKillAtTheSameScaleEvenWhenTheGameKnowsABetterTime()
	{
		tasks();

		chat("Fight duration: <col=ff0000>5:57</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>10</col>.");
		ticks(3);
		chat("Fight duration: <col=ff0000>5:40</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>11</col>.");
		ticks(3);

		List<PostPayload> bests = posted("pb");
		assertEquals(2, bests.size());
		Map<String, String> faster = bests.get(1).getFields();
		assertEquals("no", faster.get("New"));
		assertEquals("5:40", faster.get("Time"));
	}

	@Test
	public void aNewPbSeasonInTheClanConfigReportsTheFirstKillAgainEvenWhenLastSeasonWasFaster()
	{
		tasks();
		chat("Fight duration: <col=ff0000>5:40</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>10</col>.");
		ticks(3);
		chat("Fight duration: <col=ff0000>5:57</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>11</col>.");
		ticks(3);
		assertEquals(1, postedCount("pb"));

		remoteConfig.setPbSeasonStart("2026-11-16");
		chat("Fight duration: <col=ff0000>5:57</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>12</col>.");
		ticks(3);

		List<PostPayload> bests = posted("pb");
		assertEquals(2, bests.size());
		assertEquals("5:57", bests.get(1).getFields().get("Time"));
	}

	@Test
	public void endingThePbSeasonGoesBackToTheKillsRememberedSinceInstall()
	{
		tasks();
		chat("Fight duration: <col=ff0000>5:40</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>10</col>.");
		ticks(3);
		remoteConfig.setPbSeasonStart("2026-11-16");
		chat("Fight duration: <col=ff0000>5:57</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>11</col>.");
		ticks(3);
		assertEquals(2, postedCount("pb"));

		remoteConfig.setPbSeasonStart("");
		chat("Fight duration: <col=ff0000>5:57</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>12</col>.");
		ticks(3);

		assertEquals(2, postedCount("pb"));
	}

	@Test
	public void sendsStoredPersonalBestsAfterLogin()
	{
		tasks();
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfigurationKeys("personalbest", "rsprofile.abc", "")).thenReturn(List.of("zulrah"));
		when(configManager.getRSProfileConfiguration("personalbest", "zulrah", double.class)).thenReturn(55.0);

		login();
		ticks(6);

		List<PostPayload> snapshots = posted("pb-snapshot");
		assertEquals(1, snapshots.size());
		assertTrue(gson.toJson(snapshots.get(0).getEntries()).contains("\"boss\":\"zulrah\""));
	}

	@Test
	public void storedBestsSentAfterLoginCarryTheToaTotalRecordInsteadOfRuneLitesChallengeTime()
	{
		tasks();
		storedPersonalBests(Map.of("zulrah", 55.0, "tombs of amascut expert mode 3 players", 1603.0));
		when(participantReader.toaTeamSize()).thenReturn(3);
		login();

		finishExpertToaWithATotalRecordOf2957();
		login();
		ticks(6);

		String entries = latestStoredBests();
		assertTrue(entries.contains("\"boss\":\"zulrah\""));
		assertFalse(entries.contains("\"boss\":\"tombs of amascut expert mode 3 players\""));
		assertTrue(entries.contains("{\"boss\":\"tombs of amascut expert mode 3 players total\",\"seconds\":1797.6,\"time\":\"29:57.60\"}"));
	}

	@Test
	public void aToaTotalRecordOnASeasonalWorldIsNotSentWithTheMainGameStoredBests()
	{
		tasks();
		storedPersonalBests(Map.of("zulrah", 55.0));
		when(participantReader.toaTeamSize()).thenReturn(3);
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL));
		login();

		finishExpertToaWithATotalRecordOf2957();
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		login();
		ticks(6);

		assertFalse(latestStoredBests().contains("tombs of amascut"));
	}

	@Test
	public void aToaTotalRecordIsNotSentWhenTheGameShowsNoPartyToTellTheTeamSize()
	{
		tasks();
		storedPersonalBests(Map.of("zulrah", 55.0));
		when(participantReader.toaTeamSize()).thenReturn(0);
		login();

		finishExpertToaWithATotalRecordOf2957();
		login();
		ticks(6);

		assertFalse(latestStoredBests().contains("tombs of amascut"));
	}

	@Test
	public void aOneTimeToaTaskIsStillJudgedFromRuneLitesStoredBestThatTheStoredBestsSentLeaveOut()
	{
		TaskDefinition task = task("desert-dash", "RAID:Tombs of Amascut: Expert Mode", null, 0, false);
		task.setOnce(true);
		tasks(task);
		storedPersonalBest("tombs of amascut expert mode solo", 1400.0);

		login();
		ticks(6);

		assertEquals(List.of("Desert Dash"), postedTasks());
		assertFalse(latestStoredBests().contains("tombs of amascut"));
	}

	@Test
	public void aNewToaTotalRecordSendsTheStoredBestsAgainAtTheNextLogin()
	{
		tasks();
		storedPersonalBests(Map.of("zulrah", 55.0));
		when(participantReader.toaTeamSize()).thenReturn(1);
		login();
		ticks(6);
		login();
		ticks(6);
		assertEquals(1, postedCount("pb-snapshot"));

		chat("Tombs of Amascut total completion time: <col=ff0000>25:00</col> (new personal best)");
		chat("Your completed Tombs of Amascut count is: <col=ff0000>10</col>.");
		ticks(3);
		login();
		ticks(6);

		List<PostPayload> snapshots = posted("pb-snapshot");
		assertEquals(2, snapshots.size());
		assertTrue(gson.toJson(snapshots.get(1).getEntries()).contains("\"boss\":\"tombs of amascut solo total\""));
	}

	@Test
	public void countsTheClanmatesAtTheBossAsTheTeamSizeAndNamesThem()
	{
		tasks();
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy"));

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		Map<String, String> fields = payload.getValue().getFields();
		assertEquals("2", fields.get("Team size"));
		assertEquals(ME + ", Raidbuddy", fields.get("Party"));
	}

	@Test
	public void takesTheTeamSizeFromTheGameWhenItPrintsOne()
	{
		tasks();
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Thirdmate")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy", "Thirdmate"));

		chat("Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>30:00.00</col> (new personal best)");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		assertEquals("3", payload.getValue().getFields().get("Team size"));
	}

	@Test
	public void postsNoChambersPersonalBestWhenFewerPlayersAreFoundThanTheGamesTeamSize()
	{
		tasks();
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy"));

		chat("Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>30:00.00</col> (new personal best)");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		assertEquals(0, postedCount("pb"));
	}

	@Test
	public void postsAFracturedArchiveTaskFromTheTotalTimeAndTheGamesPartyList()
	{
		tasks(task("archive-crew", "RAID:Fractured Archive", "20:00", 3, true));
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Third")).thenReturn(mock(ClanMember.class));
		when(participantReader.read("RAID:Fractured Archive")).thenReturn(List.of(ME, "Raidbuddy", "Third"));

		chat("Challenge complete: Ocellus. Duration: <col=ef1020>8:50.40</col>. Total: <col=ef1020>19:07.80</col>. Personal best: 7:52.20");
		chat("The Fractured Archive: Total completion time: <col=ef1020>19:07.80</col>. Personal best: 18:41.40");
		chat("Your completed Fractured Archive count is: <col=ff0000>56</col>.");
		ticks(3);

		assertEquals(List.of("Archive Crew"), postedTasks());
	}

	@Test
	public void postsNothingForContentTheClanHasSwitchedOff()
	{
		tasks(task("archive-crew", "RAID:Fractured Archive", "20:00", 3, true), task("any-raid", "RAID:*", null, 0, false));
		remoteConfig.setIgnoredContent(List.of("Fractured Archive"));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy", "Third"));
		when(clan.findMember(anyString())).thenReturn(mock(ClanMember.class));

		chat("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)");
		chat("Your completed Fractured Archive count is: <col=ff0000>1</col>.");
		ticks(3);

		assertTrue(postedTasksDropsAndBests().isEmpty());
	}

	@Test
	public void keepsPostingOtherContentWhenOneRaidIsSwitchedOff()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		remoteConfig.setIgnoredContent(List.of("Fractured Archive"));

		chat("Tombs of Amascut total completion time: <col=ff0000>25:00</col> (new personal best)");
		chat("Your completed Tombs of Amascut count is: <col=ff0000>10</col>.");
		ticks(3);

		assertEquals(List.of("Any Raid"), postedTasks());
	}

	@Test
	public void postsAFracturedArchivePersonalBestWithTheTotalTimeAndTeamSize()
	{
		tasks();
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Third")).thenReturn(mock(ClanMember.class));
		when(participantReader.read("RAID:Fractured Archive")).thenReturn(List.of(ME, "Raidbuddy", "Third"));

		chat("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)");
		chat("Your completed Fractured Archive count is: <col=ff0000>1</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		Map<String, String> fields = payload.getValue().getFields();
		assertEquals("Fractured Archive", fields.get("Boss"));
		assertEquals("2:25.80", fields.get("Time"));
		assertEquals("total", fields.get("Time type"));
		assertEquals("3", fields.get("Team size"));
		assertEquals("yes", fields.get("New"));
	}

	@Test
	public void postsNoFracturedArchivePersonalBestWhenFewerNamesAreReadThanTheGamesPartySlots()
	{
		tasks(task("any-raid", "RAID:*", null, 0, true));
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read("RAID:Fractured Archive")).thenReturn(List.of(ME, "Raidbuddy"));
		when(participantReader.gamesTeamSize("RAID:Fractured Archive")).thenReturn(3);

		chat("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)");
		chat("Your completed Fractured Archive count is: <col=ff0000>1</col>.");
		ticks(3);

		assertTrue(postedTasksDropsAndBests().isEmpty());
	}

	@Test
	public void postsNoRaidPersonalBestWhenAnOutsiderWasInTheParty()
	{
		tasks();
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Tombs of Amascut total completion time: <col=ff0000>25:00</col> (new personal best)");
		chat("Your completed Tombs of Amascut count is: <col=ff0000>10</col>.");
		ticks(3);

		assertTrue(postedTasksDropsAndBests().isEmpty());
	}

	@Test
	public void postsNoBossPersonalBestWithAStrangerInTheInstance()
	{
		tasks();
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		assertTrue(postedTasksDropsAndBests().isEmpty());
	}

	@Test
	public void postsNoBossPersonalBestOutsideAnInstance()
	{
		tasks();
		when(worldView.isInstance()).thenReturn(false);

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		assertTrue(postedTasksDropsAndBests().isEmpty());
	}

	@Test
	public void postsASoloKillOnAnOpenWorldWhenTheGameCountsTheTeamItself()
	{
		tasks();
		when(worldView.isInstance()).thenReturn(false);
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>16:00</col> (new personal best)");
		chat("Your Nightmare kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		assertEquals("1", payload.getValue().getFields().get("Team size"));
	}

	@Test
	public void postsASoloKillAmongBystandersInTheArenaWhenTheGameCountsTheTeamItself()
	{
		tasks();
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>16:00</col> (new personal best)");
		chat("Your Nightmare kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		assertEquals("1", payload.getValue().getFields().get("Team size"));
	}

	@Test
	public void sendsNoStoredPersonalBestsBeforeRuneLiteKnowsTheGameProfile()
	{
		tasks();
		when(configManager.getRSProfileKey()).thenReturn(null);

		login();
		ticks(6);

		assertEquals(0, postedCount("pb-snapshot"));
	}

	@Test
	public void resendsAKeptPostToTheBotAddressCurrentlyInTheSettings()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Kept clog"));
		when(pendingPostStore.load(anyLong(), any())).thenReturn(List.of(pending));
		when(config.botUrl()).thenReturn("https://moved.example/api/v1/posts");

		login();
		ticks(16);

		verify(botClient).send(eq(new BotTarget("https://moved.example/api/v1/posts", TOKEN)), resendOf(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void resendsKeptPostsToTheNewAddressAsSoonAsTheBotUrlIsChanged()
	{
		tasks();
		login();
		ticks(16);
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Kept clog"));
		when(pendingPostStore.load(anyLong(), any())).thenReturn(List.of(pending));
		when(config.botUrl()).thenReturn("https://moved.example/api/v1/posts");

		ConfigChanged change = new ConfigChanged();
		change.setGroup(SlayClanConfig.GROUP);
		change.setKey(SlayClanConfig.BOT_URL_KEY);
		plugin.onConfigChanged(change);
		ArgumentCaptor<Runnable> followUp = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).schedule(followUp.capture(), anyLong(), any());
		followUp.getValue().run();

		verify(botClient).send(eq(new BotTarget("https://moved.example/api/v1/posts", TOKEN)), resendOf(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void postsToTheBotUrlWithoutTheSpacesAroundIt()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(config.botUrl()).thenReturn("  " + API + " ");

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		assertEquals(List.of("Any Raid"), postedTasks());
	}

	@Test
	public void postsNothingToAPlainHttpBotUrlOnAnotherComputer()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(config.botUrl()).thenReturn("http://bot.example/api/v1/posts");

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(botClient, never()).send(any(), any(), (byte[]) any());
	}

	@Test
	public void postsNothingAndSaysSoInChatOnADeadmanWorld()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.DEADMAN));

		login();
		ticks(16);
		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(botClient, never()).send(any(), any(), (byte[]) any());
		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("nothing is posted on this world type"), isNull());
	}

	@Test
	public void stopsPostingAfterHoppingToADeadmanWorldAndResumesBackOnMainGame()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		login();
		ticks(16);

		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.DEADMAN));
		gameState(GameState.HOPPING);
		gameState(GameState.LOGGED_IN);
		ticks(16);
		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);
		assertEquals(List.of(), postedTasks());
		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("nothing is posted on this world type"), isNull());

		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		gameState(GameState.HOPPING);
		gameState(GameState.LOGGED_IN);
		ticks(16);
		chat("Your completed Theatre of Blood count is: <col=ff0000>6</col>.");
		ticks(3);
		assertEquals(List.of("Any Raid"), postedTasks());
	}

	@Test
	public void aTaskPostWhoseScreenshotFailedTellsStaffAndWarnsThePlayer()
	{
		doAnswer(invocation ->
		{
			Consumer<byte[]> onCaptured = invocation.getArgument(0);
			onCaptured.accept(null);
			return null;
		}).when(screenshotService).capture(any());
		tasks(task("any-raid", "RAID:*", null, 0, false));
		login();
		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		PostPayload task = posted("task").get(0);
		assertEquals("screenshot_failed", task.getFields().get("Problems"));
		assertEquals(PluginVersion.VERSION, task.getFields().get("Plugin version"));
		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("the screenshot failed, the post goes to the clan without one"), isNull());
	}

	@Test
	public void aTaskPostWithAScreenshotCarriesNoProblems()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		login();
		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		assertFalse(posted("task").get(0).getFields().containsKey("Problems"));
	}

	@Test
	public void taskAndPersonalBestPostsCarryTheGamesAccountIdSoARenameCanBeRecognised()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		login();
		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);
		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		assertEquals("1", posted("task").get(0).getAccountHash());
		assertEquals("1", posted("pb").get(0).getAccountHash());
	}

	@Test
	public void postsNothingAndAsksForTheBotUrlInChatWhenTheSettingIsEmpty()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(config.botUrl()).thenReturn("");

		login();
		ticks(16);
		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(botClient, never()).send(any(), any(), (byte[]) any());
		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("clan bot URL"), isNull());
	}

	@Test
	public void aRejectedTokenIsSaidOncePerLoginHoweverManyPostsItStops()
	{
		tasks();
		when(botClient.send(any(), any(), (byte[]) any()))
			.thenReturn(CompletableFuture.completedFuture(BotReply.blocked(BotReply.Blocker.TOKEN_REJECTED, "Token not accepted.")));

		login();
		ticks(6);
		chat("New item added to your collection log: <col=ff0000>Vorki</col>");

		verify(client, times(1)).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("Token not accepted."), isNull());
		verify(pendingPostStore).add(any(), any(), anyBoolean());
	}

	@Test
	public void saysInChatWhatTheBotAnsweredAboutAPost()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(botClient.send(any(), any(), (byte[]) any()))
			.thenReturn(CompletableFuture.completedFuture(BotReply.refused("Not recorded: already completed.", "already completed.")));

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("Slay Clan: Not recorded: already completed."), isNull());
		verify(pendingPostStore, never()).add(any(), any(), anyBoolean());
	}

	@Test
	public void aPbTheBotKeptOffTheBoardStaysOutOfTheChat()
	{
		tasks();
		botKeepsEveryPostOffTheBoard("Personal best 0:55.00 at Zulrah not sent to the board: under the board minimum.");

		login();
		killWithNewPersonalBest();

		assertEquals(1, posted("pb").size());
		verify(client, never()).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("under the board minimum."), isNull());
	}

	@Test
	public void aPbTheBotKeptOffTheBoardShowsWhenTheSettingIsOn()
	{
		tasks();
		when(config.showPbNotSent()).thenReturn(true);
		botKeepsEveryPostOffTheBoard("Personal best 0:55.00 at Zulrah not sent to the board: under the board minimum.");

		login();
		killWithNewPersonalBest();

		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("Slay Clan: Personal best 0:55.00 at Zulrah not sent to the board: under the board minimum."), isNull());
	}

	@Test
	public void aPbRefusedForAMissingScreenshotShowsEvenWhenNotSentPbsAreHidden()
	{
		tasks();
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(
			BotReply.refused("Personal best not recorded: the screenshot is missing.", "the screenshot is missing.")));

		login();
		killWithNewPersonalBest();

		verify(client, atLeastOnce()).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("Slay Clan: Personal best not recorded: the screenshot is missing."), isNull());
	}

	@Test
	public void aClanTaskTheBotDidNotSendForReviewStillShowsInTheChat()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		botKeepsEveryPostOffTheBoard("Clan task Any raid (Easy) not sent for review: you cannot get points for this task right now.");

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(client).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""),
			contains("Slay Clan: Clan task Any raid (Easy) not sent for review: you cannot get points for this task right now."), isNull());
	}

	private void botKeepsEveryPostOffTheBoard(String message)
	{
		when(botClient.send(any(), any(), (byte[]) any())).thenAnswer(invocation ->
		{
			PostPayload payload = invocation.getArgument(1);
			BotReply reply = payload.postKind().isFeed() ? BotReply.recorded(null) : BotReply.notSent(message);
			return CompletableFuture.completedFuture(reply);
		});
	}

	private void killWithNewPersonalBest()
	{
		ticks(16);
		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);
	}

	@Test
	public void oneTimeTaskRefusedForTheMembersSetupPostsAgainAfterTheNextLogin()
	{
		TaskDefinition task = task("cave-runner", "KILL:TzTok-Jad", null, 0, false);
		task.setOnce(true);
		tasks(task);
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(BotReply.refused(
			"Clan task not recorded: Me is not listed as your account in SC Members.", "Me is not listed as your account in SC Members.")));

		chat("Your TzTok-Jad kill count is: <col=ff0000>1</col>.");
		ticks(3);
		chat("Your TzTok-Jad kill count is: <col=ff0000>2</col>.");
		ticks(3);

		assertEquals(List.of("Cave Runner"), postedTasks());
		assertFalse(completed.contains("cave-runner"));

		login();
		chat("Your TzTok-Jad kill count is: <col=ff0000>3</col>.");
		ticks(3);

		assertEquals(List.of("Cave Runner", "Cave Runner"), postedTasks());
	}

	@Test
	public void oneTimeTaskTheMemberAlreadyHoldsStaysDone()
	{
		TaskDefinition task = task("cave-runner", "KILL:TzTok-Jad", null, 0, false);
		task.setOnce(true);
		tasks(task);
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(
			BotReply.refused("Clan task not recorded: you already have it approved.", "you already have it approved.")));

		chat("Your TzTok-Jad kill count is: <col=ff0000>1</col>.");
		ticks(3);
		login();
		chat("Your TzTok-Jad kill count is: <col=ff0000>2</col>.");
		ticks(3);

		assertEquals(List.of("Cave Runner"), postedTasks());
		assertTrue(completed.contains("cave-runner"));
	}

	@Test
	public void combatAchievementDoneBeforeThePluginPostsItsTaskAfterLogin()
	{
		TaskDefinition task = task("no-fluke", "CA:It Wasn't a Fluke", null, 0, false);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(398, "It Wasn't a Fluke");

		login();
		ticks(6);

		assertEquals(List.of("No Fluke"), postedTasks());
	}

	@Test
	public void storedPersonalBestUnderTheLimitPostsTheSpeedTaskAfterLogin()
	{
		TaskDefinition task = task("serpent-sprint", "KILL:Zulrah", "1:00", 0, false);
		task.setOnce(true);
		tasks(task);
		storedPersonalBest("zulrah", 43.8);

		login();
		ticks(6);

		assertEquals(List.of("Serpent Sprint"), postedTasks());
	}

	@Test
	public void aTaskJudgedAtLoginIsPostedOnlyAfterTheWelcomeScreenIsClosed()
	{
		Widget welcomeScreen = mock(Widget.class);
		when(client.getWidget(InterfaceID.WelcomeScreen.UNIVERSE)).thenReturn(welcomeScreen);
		TaskDefinition task = task("no-fluke", "CA:It Wasn't a Fluke", null, 0, false);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(398, "It Wasn't a Fluke");

		login();
		ticks(30);
		assertEquals(List.of(), postedTasks());

		when(client.getWidget(InterfaceID.WelcomeScreen.UNIVERSE)).thenReturn(null);
		ticks(3);

		assertEquals(List.of("No Fluke"), postedTasks());
	}

	@Test
	public void aTaskJudgedFromTheCombatAchievementListIsPostedWithAScreenshot()
	{
		TaskDefinition task = task("no-fluke", "CA:It Wasn't a Fluke", null, 0, false);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(398, "It Wasn't a Fluke");

		login();
		ticks(6);

		assertArrayEquals(PLAIN_SCREENSHOT, screenshotBytesOfTheTaskPost());
	}

	@Test
	public void aTaskJudgedFromAStoredPersonalBestIsPostedWithAScreenshot()
	{
		TaskDefinition task = task("serpent-sprint", "KILL:Zulrah", "1:00", 0, false);
		task.setOnce(true);
		tasks(task);
		storedPersonalBest("zulrah", 43.8);

		login();
		ticks(6);

		assertArrayEquals(PLAIN_SCREENSHOT, screenshotBytesOfTheTaskPost());
	}

	@Test
	public void aCollectionLogTaskJudgedOnTheOpenedLogIsPostedWithAScreenshot()
	{
		TaskDefinition petHoarder = withItems(task("pet-hoarder", "CLOG", null, 0, false), "Vorki", "Olmlet", "Pet snakeling");
		petHoarder.setRequired(2);
		petHoarder.setOnce(true);
		tasks(petHoarder);
		login();

		openCollectionLogWith("Olmlet", "Bandos chestplate", "Pet snakeling");
		ticks(3);

		assertArrayEquals(PLAIN_SCREENSHOT, screenshotBytesOfTheTaskPost());
	}

	@Test
	public void anEquipmentTaskIsPostedWithAScreenshot()
	{
		tasks(withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape"));
		wearing("Quest point cape");
		login();

		ticks(6);

		assertArrayEquals(PLAIN_SCREENSHOT, screenshotBytesOfTheTaskPost());
	}

	@Test
	public void severalTasksJudgedAtOnceAreAllPostedWithOneScreenshot()
	{
		TaskDefinition cape = withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape");
		TaskDefinition boots = withItems(task("dragon-feet", "EQUIP", null, 0, false), "Dragon boots");
		tasks(cape, boots);
		wearing("Quest point cape", "Dragon boots");
		login();

		ticks(6);

		assertEquals(List.of("Quest Completed", "Dragon Feet"), postedTasks());
		verify(screenshotService, times(1)).capture(any());
	}

	@Test
	public void anUndeliveredTaskPostIsKeptTogetherWithItsScreenshot()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(botClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)));

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(pendingPostStore).add(any(), any(), eq(true));
	}

	@Test
	public void achievementTaskAlreadyMarkedDoneIsPostedAgainAtTheFirstLoginOnly()
	{
		TaskDefinition task = task("no-fluke", "CA:It Wasn't a Fluke", null, 0, false);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(398, "It Wasn't a Fluke");
		completed.add("no-fluke");

		login();
		ticks(6);
		login();
		ticks(6);

		assertEquals(List.of("No Fluke"), postedTasks());
	}

	@Test
	public void clanOnlyAchievementTaskIsNotPostedAtLoginForAnAchievementDoneEarlier()
	{
		TaskDefinition task = task("perfect-trio", "CA:Perfect Olm (Trio)", null, 0, true);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(400, "Perfect Olm (Trio)");

		login();
		ticks(6);

		assertTrue(postedTasks().isEmpty());
	}

	@Test
	public void achievementTaskWaitsForTheClanToBeKnownBeforeItIsPostedAtLogin()
	{
		TaskDefinition task = task("no-fluke", "CA:It Wasn't a Fluke", null, 0, false);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(398, "It Wasn't a Fluke");
		when(client.getClanSettings()).thenReturn(null);

		login();
		ticks(6);
		assertTrue(postedTasks().isEmpty());

		when(client.getClanSettings()).thenReturn(clan);
		ticks(1);

		assertEquals(List.of("No Fluke"), postedTasks());
	}

	@Test
	public void achievementCompletedRightAfterLoginIsNotPostedASecondTimeFromTheGamesList()
	{
		TaskDefinition task = task("no-fluke", "CA:It Wasn't a Fluke", null, 0, false);
		task.setOnce(true);
		tasks(task);
		completedCombatAchievement(398, "It Wasn't a Fluke");

		login();
		chat("CA_ID:398|Congratulations, you've completed a master combat task: @ach_comp@It Wasn't a Fluke</col> (6 points).");
		ticks(6);

		assertEquals(List.of("No Fluke"), postedTasks());
	}

	// Ids 384 to 415 sit in the thirteenth completion varp, so one varp stub covers every test here
	private void completedCombatAchievement(int id, String name)
	{
		StructComposition struct = mock(StructComposition.class);
		when(struct.getIntValue(CombatAchievementReader.PARAM_ID)).thenReturn(id);
		when(struct.getStringValue(CombatAchievementReader.PARAM_NAME)).thenReturn(name);
		EnumComposition easy = mock(EnumComposition.class);
		when(easy.getIntVals()).thenReturn(new int[]{7});
		when(client.getEnum(CombatAchievementReader.TIER_ENUMS[0])).thenReturn(easy);
		when(client.getStructComposition(7)).thenReturn(struct);
		when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_12)).thenReturn(1 << (id % 32));
	}

	private void storedPersonalBest(String boss, double seconds)
	{
		storedPersonalBests(Map.of(boss, seconds));
	}

	private void finishExpertToaWithATotalRecordOf2957()
	{
		chat("Tombs of Amascut: Expert Mode challenge completion time: <col=ff0000>28:38</col>. Personal best: 26:43");
		chat("Tombs of Amascut total completion time: <col=ff0000>31:30.00</col>. Personal best: 29:57.60");
		chat("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>10</col>.");
		ticks(3);
	}

	private String latestStoredBests()
	{
		List<PostPayload> snapshots = posted("pb-snapshot");
		return gson.toJson(snapshots.get(snapshots.size() - 1).getEntries());
	}

	private void storedPersonalBests(Map<String, Double> secondsByBoss)
	{
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfigurationKeys("personalbest", "rsprofile.abc", "")).thenReturn(new ArrayList<>(secondsByBoss.keySet()));
		secondsByBoss.forEach((boss, seconds) ->
			when(configManager.getRSProfileConfiguration("personalbest", boss, double.class)).thenReturn(seconds));
	}

	private static VarbitChanged varbitChanged(int varbitId, int varpId)
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(varbitId);
		event.setVarpId(varpId);
		return event;
	}

	private void login()
	{
		gameState(GameState.LOGGING_IN);
		gameState(GameState.LOGGED_IN);
	}

	private void gameState(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		plugin.onGameStateChanged(event);
	}

	private void chat(String message)
	{
		plugin.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", message, null, 0));
	}

	private void ticks(int count)
	{
		for (int i = 0; i < count; i++)
		{
			tick++;
			plugin.onGameTick(new GameTick());
		}
	}

	private void openCollectionLogWith(String... itemNames)
	{
		plugin.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		for (int i = 0; i < itemNames.length; i++)
		{
			int id = 2000 + i;
			ItemComposition composition = mock(ItemComposition.class);
			when(composition.getName()).thenReturn(itemNames[i]);
			when(composition.getMembersName()).thenReturn(itemNames[i]);
			when(itemManager.getItemComposition(id)).thenReturn(composition);
			ScriptEvent event = mock(ScriptEvent.class);
			when(event.getArguments()).thenReturn(new Object[]{0, id, 1});
			ScriptPreFired fired = new ScriptPreFired(CollectionLogCapture.COLLECTION_TRANSMIT);
			fired.setScriptEvent(event);
			plugin.onScriptPreFired(fired);
		}
	}

	private void wearing(String... itemNames)
	{
		ItemContainer worn = mock(ItemContainer.class);
		Item[] items = new Item[itemNames.length];
		for (int i = 0; i < itemNames.length; i++)
		{
			int id = 1000 + i;
			items[i] = new Item(id, 1);
			ItemComposition composition = mock(ItemComposition.class);
			when(composition.getName()).thenReturn(itemNames[i]);
			when(itemManager.getItemComposition(id)).thenReturn(composition);
		}
		when(worn.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
	}

	private void tasks(TaskDefinition... tasks)
	{
		remoteConfig.setTasks(List.of(tasks));
	}

	private String postedField(String name)
	{
		List<PostPayload> tasks = posted("task");
		assertEquals(1, tasks.size());
		return tasks.get(0).getFields().get(name);
	}

	// The activity feed goes out on its own at login, so tests about what the member did look past it
	private List<PostPayload> postedTasksDropsAndBests()
	{
		return posted().stream().filter(payload -> !payload.postKind().isFeed()).collect(Collectors.toList());
	}

	private List<String> postedTasks()
	{
		return posted("task").stream()
			.map(payload -> payload.getFields().get("Task"))
			.collect(Collectors.toList());
	}

	private List<String> postedKinds()
	{
		return posted().stream().map(PostPayload::getKind).collect(Collectors.toList());
	}

	private long postedCount(String kind)
	{
		return posted(kind).size();
	}

	private List<PostPayload> posted(String kind)
	{
		return posted().stream().filter(payload -> payload.getKind().equals(kind)).collect(Collectors.toList());
	}

	private byte[] screenshotBytesOfTheTaskPost()
	{
		ArgumentCaptor<PostPayload> payloads = ArgumentCaptor.forClass(PostPayload.class);
		ArgumentCaptor<byte[]> screenshots = ArgumentCaptor.forClass(byte[].class);
		verify(botClient, atLeast(1)).send(eq(TARGET), payloads.capture(), screenshots.capture());
		for (int i = 0; i < payloads.getAllValues().size(); i++)
		{
			if (payloads.getAllValues().get(i).getKind().equals("task"))
			{
				return screenshots.getAllValues().get(i);
			}
		}
		throw new AssertionError("no task was posted");
	}

	private List<PostPayload> posted()
	{
		ArgumentCaptor<PostPayload> payloads = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient, atLeast(0)).send(eq(TARGET), payloads.capture(), (byte[]) any());
		return payloads.getAllValues();
	}

	private static PostPayload payload(String task)
	{
		return new PostPayload("task", "0123456789abcdef", ME, null, "2026-10-10T18:00:00Z", Map.of("Task", task), null, null);
	}

	private static String judgementKey(String source, long accountHash)
	{
		return source + "." + accountHash;
	}

	@Test
	public void dropPostShowsTheGrandExchangeGuidePriceLikeTheGameBroadcastNotTheWikiPrice()
	{
		TaskDefinition drops = task("drops", "DROP", null, 0, false);
		drops.setMinValue(500_000);
		drops.setValuePerPoint(1_000_000);
		tasks(drops);
		ItemComposition horn = mock(ItemComposition.class);
		when(horn.getName()).thenReturn("Horn of Bran");
		when(itemManager.getItemComposition(HORN_OF_BRAN)).thenReturn(horn);
		when(itemManager.canonicalize(HORN_OF_BRAN)).thenReturn(HORN_OF_BRAN);
		when(itemManager.getItemPrice(HORN_OF_BRAN)).thenReturn(193_000_000L);
		when(itemManager.getItemPriceWithSource(HORN_OF_BRAN, false)).thenReturn(199_000_000L);

		plugin.onLootReceived(new LootReceived("Bran", 0, LootRecordType.NPC, List.of(new ItemStack(HORN_OF_BRAN, 1)), 1, null));

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), (byte[]) any());
		assertEquals("199", payload.getValue().getFields().get("Value in M"));
		assertEquals("199", payload.getValue().getFields().get("Points"));
	}

	@Test
	public void raidPurpleWithClanMatesIsPostedNamingTheClanParty()
	{
		tasks(withItems(task("clan-purple", "LOOT", null, 0, true), "Twisted bow"));
		raidPartyOf(ME, "Raidbuddy");

		loot("Chambers of Xeric", "Twisted bow", 1_000_000_000L);

		assertEquals(List.of("Clan Purple"), postedTasks());
		assertEquals("Clanmate, Raidbuddy", postedField("Party"));
	}

	@Test
	public void raidPurpleWithClanMatesIsNotPostedWhenAnOutsiderWasInTheRaid()
	{
		tasks(withItems(task("clan-purple", "LOOT", null, 0, true), "Scythe of vitur*"));
		raidPartyOf(ME, "Raidbuddy", OUTSIDER);

		loot("Theatre of Blood", "Scythe of vitur (uncharged)", 1_000_000_000L);

		assertEquals(List.of(), postedTasks());
	}

	@Test
	public void raidPurpleWithClanMatesIsNotPostedWhenPartOfTheRaidPartyCannotBeRead()
	{
		tasks(withItems(task("clan-purple", "LOOT", null, 0, true), "Tome of fire"));
		raidPartyOf(ME, "Raidbuddy");
		when(participantReader.gamesTeamSize("RAID:Fractured Archive")).thenReturn(3);

		loot("Fractured Archive", "Tome of fire", 1_000_000L);

		assertEquals(List.of(), postedTasks());
	}

	@Test
	public void clanOnlyLootTaskIsNotPostedFromAnOpenWorldBossSinceBystandersCannotBeToldFromTheTeam()
	{
		tasks(withItems(task("clan-loot", "LOOT", null, 0, true), "Dragon pickaxe"));
		when(worldView.isInstance()).thenReturn(false);

		loot("Callisto", "Dragon pickaxe", 5_000_000L);

		assertEquals(List.of(), postedTasks());
	}

	@Test
	public void clanOnlyLootTaskAtAnInstancedBossAloneIsPosted()
	{
		tasks(withItems(task("clan-loot", "LOOT", null, 0, true), "Tanzanite fang"));

		loot("Zulrah", "Tanzanite fang", 2_000_000L);

		assertEquals(List.of("Clan Loot"), postedTasks());
	}

	@Test
	public void lootTaskOpenToAnyTeamIsPostedWithoutAPartyAlsoWithAnOutsiderInTheRaid()
	{
		tasks(withItems(task("any-purple", "LOOT", null, 0, false), "Twisted bow"));
		raidPartyOf(ME, "Raidbuddy", OUTSIDER);

		loot("Chambers of Xeric", "Twisted bow", 1_000_000_000L);

		assertEquals(List.of("Any Purple"), postedTasks());
		assertNull(postedField("Party"));
	}

	@Test
	public void dropFromARaidOfClanMembersOnlyIsMarkedAsAClanTeamWithEveryoneInTheParty()
	{
		tasks(dropsFrom500k());
		raidPartyOf(ME, "Raidbuddy");

		loot("Tombs of Amascut", "Tumeken's shadow (uncharged)", 1_000_000_000L);

		Map<String, String> drop = postedDrop();
		assertEquals("Clanmate, Raidbuddy", drop.get("Party"));
		assertEquals("yes", drop.get("Clan team"));
	}

	@Test
	public void dropFromARaidWithAnOutsiderIsNotAClanTeamAndNamesOnlyTheClanMembers()
	{
		tasks(dropsFrom500k());
		raidPartyOf(ME, "Raidbuddy", OUTSIDER);

		loot("Theatre of Blood", "Scythe of vitur (uncharged)", 1_000_000_000L);

		Map<String, String> drop = postedDrop();
		assertEquals("Clanmate, Raidbuddy", drop.get("Party"));
		assertEquals("no", drop.get("Clan team"));
	}

	@Test
	public void dropFromAnOpenWorldBossLeavesOutTheTeamSinceBystandersCannotBeToldFromIt()
	{
		tasks(dropsFrom500k());
		when(worldView.isInstance()).thenReturn(false);
		raidPartyOf(ME, "Raidbuddy");

		loot("Callisto", "Dragon pickaxe", 5_000_000L);

		Map<String, String> drop = postedDrop();
		assertNull(drop.get("Party"));
		assertNull(drop.get("Clan team"));
	}

	@Test
	public void coxChestOpenedLongAfterTheRaidWithFewerNamesThanItsTeamSizeNeitherPostsTheClanPurpleNorJudgesTheTeam()
	{
		tasks(withItems(task("clan-purple", "LOOT", null, 0, true), "Twisted bow"), dropsFrom500k());
		raidPartyOf(ME, "Raidbuddy");
		chat("Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>30:00.00</col> Personal best: 14:00");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(20);

		loot("Chambers of Xeric", "Twisted bow", 1_000_000_000L);

		assertEquals(List.of(), postedTasks());
		assertNull(postedDrop().get("Clan team"));
	}

	@Test
	public void toaChestWithFewerNamesThanFilledPartySlotsNeitherPostsTheClanPurpleNorJudgesTheTeam()
	{
		tasks(withItems(task("clan-purple", "LOOT", null, 0, true), "Tumeken's shadow*"), dropsFrom500k());
		raidPartyOf(ME, "Raidbuddy");
		when(participantReader.gamesTeamSize("RAID:Tombs of Amascut")).thenReturn(3);

		loot("Tombs of Amascut", "Tumeken's shadow (uncharged)", 1_000_000_000L);

		assertEquals(List.of(), postedTasks());
		assertNull(postedDrop().get("Clan team"));
	}

	@Test
	public void dropWhereNoPlayerCanBeFoundLeavesOutTheTeam()
	{
		tasks(dropsFrom500k());
		when(participantReader.read(anyString())).thenReturn(List.of());

		loot("Zulrah", "Tanzanite fang", 2_000_000L);

		Map<String, String> drop = postedDrop();
		assertNull(drop.get("Party"));
		assertNull(drop.get("Clan team"));
	}

	@Test
	public void soloDropAtAnInstancedBossNamesTheMemberAndCountsAsAClanTeam()
	{
		tasks(dropsFrom500k());

		loot("Zulrah", "Tanzanite fang", 2_000_000L);

		Map<String, String> drop = postedDrop();
		assertEquals(ME, drop.get("Party"));
		assertEquals("yes", drop.get("Clan team"));
	}

	@Test
	public void fracturedArchiveDropWithPartOfThePartyUnreadLeavesOutTheTeam()
	{
		tasks(dropsFrom500k());
		raidPartyOf(ME, "Raidbuddy");
		when(participantReader.gamesTeamSize("RAID:Fractured Archive")).thenReturn(3);

		loot("Fractured Archive", "Tome of fire", 1_000_000L);

		Map<String, String> drop = postedDrop();
		assertNull(drop.get("Party"));
		assertNull(drop.get("Clan team"));
	}

	@Test
	public void raidChestIsJudgedByTheRaidPartyEvenWhenTheGameDoesNotReportAnInstance()
	{
		tasks(withItems(task("clan-purple", "LOOT", null, 0, true), "Twisted bow"), dropsFrom500k());
		when(worldView.isInstance()).thenReturn(false);
		raidPartyOf(ME, "Raidbuddy");

		loot("Chambers of Xeric", "Twisted bow", 1_000_000_000L);

		assertEquals(List.of("Clan Purple"), postedTasks());
		assertEquals("yes", postedDrop().get("Clan team"));
		verify(participantReader, atLeast(1)).read("RAID:Chambers of Xeric");
	}

	private void raidPartyOf(String... names)
	{
		for (String name : names)
		{
			if (!name.equals(OUTSIDER))
			{
				when(clan.findMember(name)).thenReturn(mock(ClanMember.class));
			}
		}
		when(participantReader.read(anyString())).thenReturn(List.of(names));
	}

	@Test
	public void valuableStackableDropIsNotPostedAndTheLogSaysStackablesDoNotCount()
	{
		tasks(dropsFrom500k());

		lootStackable("Vorkath", "Venator shard", 14_882_505L);

		assertEquals(List.of(), posted("drop"));
		assertEquals(List.of("Valuable drop Venator shard (14,882,505 gp) not posted: stackable items do not count"), valuableDropLogLines());
	}

	@Test
	public void valuableDropIsPostedWithoutALogLineAboutIt()
	{
		tasks(dropsFrom500k());

		loot("Vorkath", "Dragonbone necklace", 5_000_000L);

		assertEquals(1, posted("drop").size());
		assertEquals(List.of(), valuableDropLogLines());
	}

	@Test
	public void dropBelowTheMinimumValueIsNotPostedAndLeavesNoLogLine()
	{
		tasks(dropsFrom500k());

		loot("Vorkath", "Rune platebody", 38_000L);

		assertEquals(List.of(), posted("drop"));
		assertEquals(List.of(), valuableDropLogLines());
	}

	@Test
	public void valuableDropWithoutAClanTokenIsNotPostedAndTheLogNamesTheMissingToken()
	{
		tasks(dropsFrom500k());
		when(config.clanToken()).thenReturn("");

		loot("Vorkath", "Dragonbone necklace", 5_000_000L);

		assertEquals(List.of(), posted("drop"));
		assertEquals(List.of("Valuable drop Dragonbone necklace (5,000,000 gp) not posted: posting blocked (clan token not set)"),
			valuableDropLogLines());
	}

	@Test
	public void secondValuableDropWithoutAClanTokenInTheSameLoginLeavesNoFurtherLogLine()
	{
		tasks(dropsFrom500k());
		when(config.clanToken()).thenReturn("");

		loot("Vorkath", "Dragonbone necklace", 5_000_000L);
		loot("Vorkath", "Draconic visage", 3_000_000L);

		assertEquals(1, valuableDropLogLines().size());
	}

	@Test
	public void valuableDropWithThePluginTurnedOffLeavesNoLogLine()
	{
		tasks(dropsFrom500k());
		when(remoteConfigService.current()).thenReturn(null);

		loot("Vorkath", "Dragonbone necklace", 5_000_000L);

		assertEquals(List.of(), posted("drop"));
		assertEquals(List.of(), valuableDropLogLines());
	}

	@Test
	public void stackableDropPostedByAnotherDropTaskIsNotLoggedAsLeftOut()
	{
		TaskDefinition stackablesToo = dropsFrom500k();
		stackablesToo.setId("drops-with-stackables");
		stackablesToo.setIncludeStackable(true);
		tasks(dropsFrom500k(), stackablesToo);

		lootStackable("Vorkath", "Venator shard", 14_882_505L);

		assertEquals(1, posted("drop").size());
		assertEquals(List.of(), valuableDropLogLines());
	}

	@Test
	public void lootWithoutADropTaskInTheClanConfigSaysSoOnceInTheLog()
	{
		tasks();

		loot("Vorkath", "Dragonbone necklace", 5_000_000L);
		loot("Vorkath", "Dragonbone necklace", 5_000_000L);

		assertEquals(1, pluginLog.list.stream()
			.filter(event -> event.getFormattedMessage().equals("No DROP task in the clan config, so valuable drops are not posted"))
			.count());
	}

	@Test
	public void valuableDropOutsideTheConfiguredClanIsNotPostedAndTheLogSaysSo()
	{
		tasks(dropsFrom500k());
		when(clan.getName()).thenReturn("Other Clan");

		loot("Vorkath", "Dragonbone necklace", 5_000_000L);

		assertEquals(List.of(), posted("drop"));
		assertEquals(List.of("Valuable drop Dragonbone necklace (5,000,000 gp) not posted: not in the configured clan Slay Clan"),
			valuableDropLogLines());
	}

	private List<String> valuableDropLogLines()
	{
		return pluginLog.list.stream()
			.filter(event -> event.getLevel() == Level.INFO)
			.map(ILoggingEvent::getFormattedMessage)
			.filter(line -> line.startsWith("Valuable drop"))
			.collect(Collectors.toList());
	}

	private static Logger pluginLogger()
	{
		return (Logger) LoggerFactory.getLogger(SlayClanPlugin.class);
	}

	private void lootStackable(String source, String itemName, long price)
	{
		loot(source, itemName, price, true);
	}

	private void loot(String source, String itemName, long price)
	{
		loot(source, itemName, price, false);
	}

	private void loot(String source, String itemName, long price, boolean stackable)
	{
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn(itemName);
		when(item.isStackable()).thenReturn(stackable);
		when(itemManager.getItemComposition(HORN_OF_BRAN)).thenReturn(item);
		when(itemManager.canonicalize(HORN_OF_BRAN)).thenReturn(HORN_OF_BRAN);
		when(itemManager.getItemPriceWithSource(HORN_OF_BRAN, false)).thenReturn(price);
		plugin.onLootReceived(new LootReceived(source, 0, LootRecordType.EVENT, List.of(new ItemStack(HORN_OF_BRAN, 1)), 1, null));
	}

	private static TaskDefinition dropsFrom500k()
	{
		TaskDefinition drops = task("drops", "DROP", null, 0, false);
		drops.setMinValue(500_000);
		drops.setValuePerPoint(1_000_000);
		return drops;
	}

	private Map<String, String> postedDrop()
	{
		List<PostPayload> drops = posted("drop");
		assertEquals(1, drops.size());
		return drops.get(0).getFields();
	}

	private static TaskDefinition task(String id, String trigger, String maxTime, int teamSize, boolean clanOnly)
	{
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		task.setName(name(id));
		task.setTrigger(trigger);
		task.setTier("Test");
		task.setPoints(10);
		task.setMaxTime(maxTime);
		task.setTeamSize(teamSize);
		task.setClanOnly(clanOnly);
		return task;
	}

	private static TaskDefinition withItems(TaskDefinition task, String... items)
	{
		task.setItems(List.of(items));
		return task;
	}

	private static String name(String id)
	{
		StringBuilder name = new StringBuilder();
		for (String word : id.split("-"))
		{
			name.append(name.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name.toString();
	}

	// A resent post carries its try number, so it is matched on the post it repeats
	private static PostPayload resendOf(PostPayload payload)
	{
		return argThat(sent -> sent != null && payload.getNonce().equals(sent.getNonce()));
	}

}
