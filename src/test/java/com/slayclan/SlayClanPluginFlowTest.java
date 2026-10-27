package com.slayclan;

import com.google.gson.Gson;
import com.slayclan.activity.CollectionLogCapture;
import com.slayclan.config.RemoteConfig;
import com.slayclan.config.RemoteConfigService;
import com.slayclan.config.TaskDefinition;
import com.slayclan.discord.Attachment;
import com.slayclan.discord.DiscordWebhookClient;
import com.slayclan.discord.PendingPost;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.ScreenshotService;
import com.slayclan.discord.WebhookPayload;
import com.slayclan.progress.TaskProgressStore;
import com.slayclan.raid.RaidParticipantReader;
import java.time.Instant;
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
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptEvent;
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
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.account.AccountSession;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.account.SessionManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.http.api.item.ItemPrice;
import static org.junit.Assert.assertEquals;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Drives the plugin through game events and checks what reaches Discord. Memory is faked with sets so the
 * per-account baseline and one-time logic run for real.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SlayClanPluginFlowTest
{
	private static final String WEBHOOK = "https://discord.com/api/webhooks/1/abc";
	private static final String ACTIVITY = "https://discord.com/api/webhooks/2/def";
	private static final String PB = "https://discord.com/api/webhooks/3/ghi";
	private static final String ME = "Clanmate";
	private static final String OUTSIDER = "Stranger";

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
	private DiscordWebhookClient webhookClient;
	@Mock
	private ItemManager itemManager;
	@Mock
	private TaskProgressStore progressStore;
	@Mock
	private PendingPostStore pendingPostStore;
	@Mock
	private PluginManager pluginManager;
	@Mock
	private ConfigManager configManager;
	@Mock
	private SessionManager sessionManager;
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
	private final Set<String> completed = new HashSet<>();
	private final Set<String> baselined = new HashSet<>();
	private int tick = 100;

	@Before
	public void setUp()
	{
		remoteConfig.setClanName("Slay Clan");
		remoteConfig.setPointsWebhookUrl(WEBHOOK);
		when(remoteConfigService.current()).thenReturn(remoteConfig);
		when(remoteConfigService.refresh(any())).thenReturn(CompletableFuture.completedFuture(null));
		when(config.enabled()).thenReturn(true);
		when(config.configUrl()).thenReturn("https://example.test/config.json");
		when(config.discordName()).thenReturn(ME);
		when(sessionManager.getAccountSession()).thenReturn(mock(AccountSession.class));
		when(pluginManager.getPlugins()).thenReturn(List.of());
		when(client.getLocalPlayer()).thenReturn(localPlayer);
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
		when(client.getClanSettings()).thenReturn(clan);
		EnumComposition noTasks = mock(EnumComposition.class);
		when(noTasks.getIntVals()).thenReturn(new int[0]);
		when(noTasks.getKeys()).thenReturn(new int[0]);
		when(client.getEnum(anyInt())).thenReturn(noTasks);
		when(clan.getName()).thenReturn("Slay Clan");
		when(clan.findMember(ME)).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME));
		when(webhookClient.send(anyString(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(true));
		when(webhookClient.send(anyString(), any(), (Attachment) any())).thenReturn(CompletableFuture.completedFuture(true));
		remoteConfig.setActivityWebhookUrl(ACTIVITY);
		doAnswer(invocation ->
		{
			Consumer<byte[]> onCaptured = invocation.getArgument(0);
			onCaptured.accept(new byte[]{1});
			return null;
		}).when(screenshotService).capture(any());
		when(progressStore.isCompleted(anyString())).thenAnswer(invocation -> completed.contains(invocation.<String>getArgument(0)));
		doAnswer(invocation -> completed.add(invocation.getArgument(0))).when(progressStore).markCompleted(anyString());
		when(progressStore.isBaselined(anyString(), anyLong()))
			.thenAnswer(invocation -> baselined.contains(baselineKey(invocation.getArgument(0), invocation.getArgument(1))));
		doAnswer(invocation -> baselined.add(baselineKey(invocation.getArgument(0), invocation.getArgument(1))))
			.when(progressStore).markBaselined(anyString(), anyLong());
		plugin.startUp();
	}

	@After
	public void tearDown()
	{
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

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
	}

	@Test
	public void skipsCoxWhenFewerPlayersAreFoundThanTheTeamSize()
	{
		tasks(task("clan-raid", "RAID:Chambers of Xeric*", null, 0, true));
		when(participantReader.read(anyString())).thenReturn(List.of(ME));

		chat("Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>30:00.00</col> Personal best: 14:00");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
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

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(WEBHOOK), payload.capture(), (byte[]) any());
		assertEquals(new WebhookPayload.Field("Party", "Clanmate, Raidbuddy, Third", false),
			payload.getValue().getEmbeds().get(0).getFields().get(4));
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

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(WEBHOOK), payload.capture(), (byte[]) any());
		assertEquals(4, payload.getValue().getEmbeds().get(0).getFields().size());
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
	public void firstThresholdCheckOnlyRecordsEquipmentAlreadyWorn()
	{
		tasks(withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape"));
		wearing("Quest point cape");

		login();
		ticks(6);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
		assertEquals(Set.of("quest-completed"), completed);
	}

	@Test
	public void postsEquipmentWornAfterTheBaseline()
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
	public void baselineOnAnAltDoesNotMakeTheMainsCapeLookNew()
	{
		tasks(withItems(task("quest-completed", "EQUIP", null, 0, false), "Quest point cape"));
		when(client.getAccountHash()).thenReturn(2L);
		wearing();
		login();
		ticks(6);

		when(client.getAccountHash()).thenReturn(1L);
		wearing("Quest point cape");
		login();
		ticks(6);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
	}

	@Test
	public void keepsUndeliveredPostForRetry()
	{
		tasks(task("any-raid", "RAID:*", null, 0, false));
		when(webhookClient.send(anyString(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(false));

		chat("Your completed Theatre of Blood count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(pendingPostStore).add(any(), any());
	}

	@Test
	public void runsOneRetryRoundAtATime()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Old post"), null);
		when(pendingPostStore.load(any())).thenReturn(List.of(pending));
		when(webhookClient.send(anyString(), any(), (byte[]) any())).thenReturn(new CompletableFuture<>());

		login();
		ticks(16);
		login();
		ticks(16);

		verify(webhookClient, times(1)).send(eq(WEBHOOK), eq(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void startsANewRetryRoundOnceTheLastOneFinished()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Old post"), null);
		when(pendingPostStore.load(any())).thenReturn(List.of(pending));
		when(webhookClient.send(anyString(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(false));

		login();
		ticks(16);
		login();
		ticks(16);

		verify(webhookClient, times(2)).send(eq(WEBHOOK), eq(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void releasesTheRetryLockWhenLoadingThePendingPostsFails()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Old post"), null);
		when(pendingPostStore.load(any())).thenThrow(new IllegalStateException("broken")).thenReturn(List.of(pending));

		login();
		ticks(16);
		login();
		ticks(16);

		verify(webhookClient, times(1)).send(eq(WEBHOOK), eq(pending.getPayload()), (byte[]) any());
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

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(ACTIVITY), payload.capture(), (Attachment) isNull());
		List<WebhookPayload.Field> fields = payload.getValue().getEmbeds().get(0).getFields();
		assertEquals("Vorki", fields.get(1).getValue());
		assertEquals("21992", fields.get(2).getValue());
	}

	@Test
	public void postsEveryCombatAchievementToTheActivityWebhook()
	{
		tasks();

		chat("CA_ID:398|Congratulations, you've completed an easy combat task: @ach_comp@Into the Den of Giants</col> (1 point).");

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(ACTIVITY), payload.capture(), (Attachment) isNull());
		List<WebhookPayload.Field> fields = payload.getValue().getEmbeds().get(0).getFields();
		assertEquals("398", fields.get(2).getValue());
		assertEquals("Easy", fields.get(3).getValue());
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

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		ArgumentCaptor<Attachment> attachment = ArgumentCaptor.forClass(Attachment.class);
		verify(webhookClient, times(2)).send(eq(ACTIVITY), payload.capture(), attachment.capture());
		assertEquals("SPOILER_ca-snapshot.json", attachment.getAllValues().get(0).getFilename());
		assertNull(attachment.getAllValues().get(1));
		assertEquals("Progress", payload.getAllValues().get(1).getEmbeds().get(0).getDescription());
	}

	@Test
	public void waitsWithStatusAndSnapshotsUntilTheGameHasSentTheAccountHash()
	{
		tasks();
		when(client.getAccountHash()).thenReturn(-1L);
		login();
		ticks(6);

		verify(webhookClient, never()).send(eq(ACTIVITY), any(), (Attachment) any());

		when(client.getAccountHash()).thenReturn(1L);
		ticks(1);

		verify(webhookClient, times(2)).send(eq(ACTIVITY), any(), (Attachment) any());
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

		ArgumentCaptor<Attachment> attachment = ArgumentCaptor.forClass(Attachment.class);
		verify(webhookClient, times(1)).send(eq(ACTIVITY), any(), attachment.capture());
		assertNull(attachment.getValue());

		when(client.getEnum(anyInt())).thenReturn(empty);
		ticks(1);

		verify(webhookClient, times(2)).send(eq(ACTIVITY), any(), attachment.capture());
		assertEquals("SPOILER_ca-snapshot.json", attachment.getValue().getFilename());
	}

	@Test
	public void sendsStatusAgainOnlyWhenACollectionLogOrCombatAchievementValueChanges()
	{
		tasks();
		login();
		ticks(6);
		verify(webhookClient, times(1)).send(eq(ACTIVITY), any(), (Attachment) isNull());

		plugin.onVarbitChanged(varbitChanged(VarbitID.CHAT_FILTER_ASSISTSYSTEM, -1));
		ticks(1);
		verify(webhookClient, times(1)).send(eq(ACTIVITY), any(), (Attachment) isNull());

		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY)).thenReturn(1);
		plugin.onVarbitChanged(varbitChanged(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY, -1));
		ticks(1);
		verify(webhookClient, times(2)).send(eq(ACTIVITY), any(), (Attachment) isNull());
	}

	@Test
	public void postsNothingToTheActivityWebhookWhenTheConfigHasNone()
	{
		tasks();
		remoteConfig.setActivityWebhookUrl(null);

		chat("CA_ID:398|Congratulations, you've completed an easy combat task: @ach_comp@Into the Den of Giants</col> (1 point).");
		login();
		ticks(6);

		verify(webhookClient, never()).send(eq(ACTIVITY), any(), (Attachment) any());
	}

	@Test
	public void postsANewPersonalBestWithScreenshotToThePbWebhook()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(PB), payload.capture(), (byte[]) any());
		List<WebhookPayload.Field> fields = payload.getValue().getEmbeds().get(0).getFields();
		assertEquals("Zulrah", fields.get(1).getValue());
		assertEquals("0:55", fields.get(2).getValue());
		assertEquals("yes", fields.get(6).getValue());
	}

	@Test
	public void reportsTheFirstKillAtAScaleAndTheGamesBestOnceForABoss()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		login();

		chat("Fight duration: <col=ff0000>1:10</col>. Personal best: 0:55");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient, times(2)).send(eq(PB), payload.capture(), (byte[]) any());
		WebhookPayload.Embed fastest = payload.getAllValues().get(0).getEmbeds().get(0);
		assertEquals("Fastest kill at this scale since the plugin was installed", fastest.getDescription());
		assertEquals("1:10", fastest.getFields().get(2).getValue());
		assertEquals("1", fastest.getFields().get(4).getValue());
		WebhookPayload.Embed current = payload.getAllValues().get(1).getEmbeds().get(0);
		assertEquals("Current personal best", current.getDescription());
		assertEquals("0:55", current.getFields().get(2).getValue());
		assertEquals("Scale", current.getFields().get(4).getName());

		chat("Fight duration: <col=ff0000>1:12</col>. Personal best: 0:55");
		chat("Your Zulrah kill count is: <col=ff0000>11</col>.");
		ticks(3);
		login();
		chat("Fight duration: <col=ff0000>1:12</col>. Personal best: 0:55");
		chat("Your Zulrah kill count is: <col=ff0000>12</col>.");
		ticks(3);

		verify(webhookClient, times(2)).send(eq(PB), any(), (byte[]) any());
	}

	@Test
	public void reportsAFasterKillAtTheSameScaleEvenWhenTheGameKnowsABetterTime()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);

		chat("Fight duration: <col=ff0000>5:57</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>10</col>.");
		ticks(3);
		chat("Fight duration: <col=ff0000>5:40</col>. Personal best: 3:09");
		chat("Your Yama kill count is: <col=ff0000>11</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient, times(3)).send(eq(PB), payload.capture(), (byte[]) any());
		WebhookPayload.Embed faster = payload.getAllValues().get(2).getEmbeds().get(0);
		assertEquals("Fastest kill at this scale since the plugin was installed", faster.getDescription());
		assertEquals("5:40", faster.getFields().get(2).getValue());
	}

	@Test
	public void sendsStoredPersonalBestsAfterLogin()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfigurationKeys("personalbest", "rsprofile.abc", "")).thenReturn(List.of("zulrah"));
		when(configManager.getRSProfileConfiguration("personalbest", "zulrah", double.class)).thenReturn(55.0);

		login();
		ticks(6);

		ArgumentCaptor<Attachment> attachment = ArgumentCaptor.forClass(Attachment.class);
		verify(webhookClient).send(eq(PB), any(), attachment.capture());
		assertEquals("SPOILER_pb-snapshot.json", attachment.getValue().getFilename());
		assertTrue(new String(attachment.getValue().getBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("\"boss\":\"zulrah\""));
	}

	@Test
	public void countsTheClanmatesAtTheBossAsTheTeamSizeAndNamesThem()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy"));

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(PB), payload.capture(), (byte[]) any());
		List<WebhookPayload.Field> fields = payload.getValue().getEmbeds().get(0).getFields();
		assertEquals("Team size", fields.get(4).getName());
		assertEquals("2", fields.get(4).getValue());
		assertEquals(ME + ", Raidbuddy", fields.get(5).getValue());
	}

	@Test
	public void takesTheTeamSizeFromTheGameWhenItPrintsOne()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Thirdmate")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy", "Thirdmate"));

		chat("Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>30:00.00</col> (new personal best)");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(PB), payload.capture(), (byte[]) any());
		assertEquals("3", payload.getValue().getEmbeds().get(0).getFields().get(4).getValue());
	}

	@Test
	public void postsNoChambersPersonalBestWhenFewerPlayersAreFoundThanTheGamesTeamSize()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy"));

		chat("Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>30:00.00</col> (new personal best)");
		chat("Your completed Chambers of Xeric count is: <col=ff0000>5</col>.");
		ticks(3);

		verify(webhookClient, never()).send(eq(PB), any(), (byte[]) any());
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
		remoteConfig.setPbWebhookUrl(PB);
		remoteConfig.setIgnoredContent(List.of("Fractured Archive"));
		when(participantReader.read(anyString())).thenReturn(List.of(ME, "Raidbuddy", "Third"));
		when(clan.findMember(anyString())).thenReturn(mock(ClanMember.class));

		chat("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)");
		chat("Your completed Fractured Archive count is: <col=ff0000>1</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
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
		remoteConfig.setPbWebhookUrl(PB);
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(clan.findMember("Third")).thenReturn(mock(ClanMember.class));
		when(participantReader.read("RAID:Fractured Archive")).thenReturn(List.of(ME, "Raidbuddy", "Third"));

		chat("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)");
		chat("Your completed Fractured Archive count is: <col=ff0000>1</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(PB), payload.capture(), (byte[]) any());
		List<WebhookPayload.Field> fields = payload.getValue().getEmbeds().get(0).getFields();
		assertEquals("Fractured Archive", fields.get(1).getValue());
		assertEquals("2:25.80", fields.get(2).getValue());
		assertEquals("total", fields.get(3).getValue());
		assertEquals("3", fields.get(4).getValue());
		assertEquals("yes", fields.get(6).getValue());
	}

	@Test
	public void postsNoFracturedArchivePersonalBestWhenFewerNamesAreReadThanTheGamesPartySlots()
	{
		tasks(task("any-raid", "RAID:*", null, 0, true));
		remoteConfig.setPbWebhookUrl(PB);
		when(clan.findMember("Raidbuddy")).thenReturn(mock(ClanMember.class));
		when(participantReader.read("RAID:Fractured Archive")).thenReturn(List.of(ME, "Raidbuddy"));
		when(participantReader.gamesTeamSize("RAID:Fractured Archive")).thenReturn(3);

		chat("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)");
		chat("Your completed Fractured Archive count is: <col=ff0000>1</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
	}

	@Test
	public void postsNoRaidPersonalBestWhenAnOutsiderWasInTheParty()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Tombs of Amascut total completion time: <col=ff0000>25:00</col> (new personal best)");
		chat("Your completed Tombs of Amascut count is: <col=ff0000>10</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
	}

	@Test
	public void postsNoBossPersonalBestWithAStrangerInTheInstance()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
	}

	@Test
	public void postsNoBossPersonalBestOutsideAnInstance()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(worldView.isInstance()).thenReturn(false);

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
	}

	@Test
	public void postsASoloKillOnAnOpenWorldWhenTheGameCountsTheTeamItself()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(worldView.isInstance()).thenReturn(false);
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>16:00</col> (new personal best)");
		chat("Your Nightmare kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(PB), payload.capture(), (byte[]) any());
		assertEquals("1", payload.getValue().getEmbeds().get(0).getFields().get(4).getValue());
	}

	@Test
	public void postsASoloKillAmongBystandersInTheArenaWhenTheGameCountsTheTeamItself()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(participantReader.read(anyString())).thenReturn(List.of(ME, OUTSIDER));

		chat("Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>16:00</col> (new personal best)");
		chat("Your Nightmare kill count is: <col=ff0000>10</col>.");
		ticks(3);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(PB), payload.capture(), (byte[]) any());
		assertEquals("1", payload.getValue().getEmbeds().get(0).getFields().get(4).getValue());
	}

	@Test
	public void sendsNoStoredPersonalBestsBeforeRuneLiteKnowsTheGameProfile()
	{
		tasks();
		remoteConfig.setPbWebhookUrl(PB);
		when(configManager.getRSProfileKey()).thenReturn(null);

		login();
		ticks(6);

		verify(webhookClient, never()).send(eq(PB), any(), (Attachment) any());
	}

	@Test
	public void retriesAKeptActivityPostToTheWebhookTheClanConfigHasNow()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Kept clog"), PostKind.CLOG);
		when(pendingPostStore.load(any())).thenReturn(List.of(pending));
		remoteConfig.setActivityWebhookUrl("https://discord.com/api/webhooks/9/rotated");

		login();
		ticks(16);

		verify(webhookClient).send(eq("https://discord.com/api/webhooks/9/rotated"), eq(pending.getPayload()), (byte[]) any());
	}

	@Test
	public void dropsAKeptPersonalBestPostWhenTheClanHasTurnedPersonalBestsOff()
	{
		tasks();
		PendingPost pending = new PendingPost(Instant.now().toString(), payload("Kept pb"), PostKind.PB);
		when(pendingPostStore.load(any())).thenReturn(List.of(pending));

		login();
		ticks(16);

		verify(webhookClient, never()).send(anyString(), eq(pending.getPayload()), (byte[]) any());
		verify(pendingPostStore).remove(pending);
	}

	private static VarbitChanged varbitChanged(int varbitId, int varpId)
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(varbitId);
		event.setVarpId(varpId);
		return event;
	}

	@Test
	public void postsNoPersonalBestWithoutAPbWebhook()
	{
		tasks();

		chat("Fight duration: <col=ff0000>0:55</col> (new personal best)");
		chat("Your Zulrah kill count is: <col=ff0000>10</col>.");
		ticks(3);

		verify(webhookClient, never()).send(anyString(), any(), (byte[]) any());
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

	private List<String> postedTasks()
	{
		ArgumentCaptor<WebhookPayload> payloads = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient, atLeast(0)).send(eq(WEBHOOK), payloads.capture(), (byte[]) any());
		return payloads.getAllValues().stream()
			.map(payload -> payload.getEmbeds().get(0).getFields().get(0).getValue())
			.collect(Collectors.toList());
	}

	private static WebhookPayload payload(String title)
	{
		return new WebhookPayload(List.of(new WebhookPayload.Embed(title, "", 0, "2026-10-10T18:00:00Z", null, List.of(), null)));
	}

	private static String baselineKey(String taskId, long accountHash)
	{
		return taskId + "." + accountHash;
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
}
