package com.slayclan.activity;

import com.google.gson.Gson;
import com.slayclan.discord.BotClient;
import com.slayclan.discord.BotReply;
import com.slayclan.discord.BotTarget;
import com.slayclan.discord.PendingPost;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.PostPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActivityReporterTest
{
	private static final BotTarget TARGET = new BotTarget("https://bot.example/api/v1/posts", "sc_test");
	private static final long ACCOUNT = 42L;

	private BotClient botClient;
	private PendingPostStore pendingPostStore;
	private DeliveredSnapshots deliveredSnapshots;
	private final List<BotReply> told = new ArrayList<>();
	private ActivityReporter reporter;

	@Before
	public void setUp()
	{
		botClient = mock(BotClient.class);
		pendingPostStore = mock(PendingPostStore.class);
		deliveredSnapshots = mock(DeliveredSnapshots.class);
		when(botClient.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BotReply.delivered(null)));
		reporter = new ActivityReporter(new ActivityFormatter(new Gson()), botClient, pendingPostStore, deliveredSnapshots, told::add);
	}

	@Test
	public void keptPostsAreSentOneAtATimeAndDroppedOnceTheBotTookThem()
	{
		PendingPost first = new PendingPost("2026-10-10T18:00:00Z", payload("Old post"));
		PendingPost second = new PendingPost("2026-10-10T18:00:01Z", payload("Older post"));
		when(botClient.send(any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)), CompletableFuture.completedFuture(BotReply.delivered(null)));

		reporter.retry(() -> TARGET, List.of(first, second));

		verify(botClient, times(2)).send(eq(TARGET), any(), isNull());
		verify(pendingPostStore, never()).remove(first);
		verify(pendingPostStore).remove(second);
	}

	@Test
	public void aBotUrlChangedDuringTheRetryRoundIsUsedForTheRemainingPosts()
	{
		PendingPost first = new PendingPost("2026-10-10T18:00:00Z", payload("Old post"));
		PendingPost second = new PendingPost("2026-10-10T18:00:01Z", payload("Older post"));
		BotTarget moved = new BotTarget("https://moved.example/api/v1/posts", "sc_test");
		java.util.Iterator<BotTarget> targets = List.of(TARGET, moved).iterator();

		reporter.retry(targets::next, List.of(first, second));

		verify(botClient).send(eq(TARGET), eq(first.getPayload()), isNull());
		verify(botClient).send(eq(moved), eq(second.getPayload()), isNull());
	}

	@Test
	public void aRejectedTokenStopsTheRetryRoundAfterTheFirstPost()
	{
		PendingPost first = new PendingPost("2026-10-10T18:00:00Z", payload("Old post"));
		PendingPost second = new PendingPost("2026-10-10T18:00:01Z", payload("Older post"));
		when(botClient.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BotReply.blocked(BotReply.Blocker.TOKEN_REJECTED, "Token not accepted.")));

		reporter.retry(() -> TARGET, List.of(first, second));

		verify(botClient, times(1)).send(eq(TARGET), any(), isNull());
		verify(pendingPostStore, never()).remove(any());
		assertEquals(1, told.size());
	}

	private static PostPayload payload(String task)
	{
		return new PostPayload("task", "0123456789abcdef", "Clanmate", null, "2026-10-10T18:00:00Z", Map.of("Task", task), null, false);
	}

	@Test
	public void sendsAClogEventWithoutScreenshot()
	{
		reporter.reportClog(TARGET, "Clanmate", "Vorki", 21992, 1, 141);

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), isNull());
		assertEquals("Vorki", payload.getValue().getFields().get("Item"));
	}

	@Test
	public void keepsAnEventTheBotCouldNotTake()
	{
		when(botClient.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)));

		reporter.reportCa(TARGET, "Clanmate", "Into the Den of Giants", 398, "Easy");

		ArgumentCaptor<PostPayload> kept = ArgumentCaptor.forClass(PostPayload.class);
		verify(pendingPostStore).add(kept.capture(), any());
		assertEquals("ca", kept.getValue().getKind());
	}

	@Test
	public void keepsAPersonalBestTheBotCouldNotTake()
	{
		when(botClient.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)));
		PostPayload payload = new ActivityFormatter(new Gson()).currentBossBest("Clanmate", "Yama", "3:09",
			com.slayclan.trigger.TimedLine.Kind.SINGLE, java.time.Instant.EPOCH);

		reporter.send(TARGET, payload, null);

		verify(pendingPostStore).add(eq(payload), any());
	}

	@Test
	public void saysInChatWhatTheBotAnsweredAboutAPersonalBest()
	{
		when(botClient.send(any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(BotReply.delivered("Not recorded: slower than your time on the board.")));
		PostPayload payload = new ActivityFormatter(new Gson()).currentBossBest("Clanmate", "Yama", "3:09",
			com.slayclan.trigger.TimedLine.Kind.SINGLE, java.time.Instant.EPOCH);

		reporter.send(TARGET, payload, null);

		assertEquals("Not recorded: slower than your time on the board.", told.get(0).getMessage());
		verify(pendingPostStore, never()).add(any(), any());
	}

	@Test
	public void remembersTheNewestStatusWhenTheBotAnswersAnOlderOneLast()
	{
		CompletableFuture<BotReply> first = new CompletableFuture<>();
		CompletableFuture<BotReply> second = new CompletableFuture<>();
		when(botClient.send(any(), any(), any())).thenReturn(first, second);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, new CombatAchievementStatus(new int[6], new int[6]), clog);
		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, new CombatAchievementStatus(new int[]{1, 0, 0, 0, 0, 0}, new int[6]), clog);
		second.complete(BotReply.delivered(null));
		first.complete(BotReply.delivered(null));

		ArgumentCaptor<String> remembered = ArgumentCaptor.forClass(String.class);
		verify(deliveredSnapshots, times(1)).markDelivered(eq(PostKind.STATUS), eq(ACCOUNT), remembered.capture());
		assertEquals(DeliveredSnapshots.fingerprint(String.join("|", "Clanmate", "1/0/0/0/0/0", "0/0/0/0/0/0", "10/100", "0/0, 0/0, 0/0, 0/0, 0/0")),
			remembered.getValue());
	}

	@Test
	public void sendsAStatusAgainAfterTheFirstAttemptFailed()
	{
		when(botClient.send(any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)), CompletableFuture.completedFuture(BotReply.delivered(null)));
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[6], new int[6]);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, ca, clog);
		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, ca, clog);

		verify(botClient, times(2)).send(eq(TARGET), any(), isNull());
		verify(deliveredSnapshots, times(1)).markDelivered(eq(PostKind.STATUS), eq(ACCOUNT), anyString());
	}

	@Test
	public void sendsStatusAndRemembersItAsDelivered()
	{
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[]{1, 0, 0, 0, 0, 0}, new int[6]);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, ca, clog);

		verify(botClient).send(eq(TARGET), any(), isNull());
		verify(deliveredSnapshots).markDelivered(eq(PostKind.STATUS), eq(ACCOUNT), anyString());
		verify(pendingPostStore, never()).add(any(), any());
	}

	@Test
	public void sendsStatusOnceWhenCheckedAgainBeforeTheBotAnswers()
	{
		when(botClient.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[6], new int[6]);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, ca, clog);
		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT, ca, clog);

		verify(botClient, times(1)).send(eq(TARGET), any(), isNull());
	}

	@Test
	public void skipsStatusTheBotAlreadyHas()
	{
		when(deliveredSnapshots.isDelivered(eq(PostKind.STATUS), anyLong(), anyString())).thenReturn(true);

		reporter.reportStatusIfChanged(TARGET, "Clanmate", ACCOUNT,
			new CombatAchievementStatus(new int[6], new int[6]), new CollectionLogStatus(10, 100, new int[5], new int[5]));

		verify(botClient, never()).send(any(), any(), any());
	}

	@Test
	public void sendsAChangedSnapshotWithItsEntriesAndRemembersIt()
	{
		reporter.reportClogSnapshot(TARGET, "Clanmate", ACCOUNT, List.of(new OwnedItem(21992, "Vorki", 1)));

		ArgumentCaptor<PostPayload> payload = ArgumentCaptor.forClass(PostPayload.class);
		verify(botClient).send(eq(TARGET), payload.capture(), isNull());
		assertEquals("clog-snapshot", payload.getValue().getKind());
		assertEquals(1, payload.getValue().getEntries().size());
		verify(deliveredSnapshots).markDelivered(eq(PostKind.CLOG_SNAPSHOT), eq(ACCOUNT), anyString());
	}

	@Test
	public void skipsASnapshotTheBotAlreadyHas()
	{
		when(deliveredSnapshots.isDelivered(eq(PostKind.PB_SNAPSHOT), anyLong(), anyString())).thenReturn(true);

		reporter.reportPbSnapshot(TARGET, "Clanmate", ACCOUNT, List.of(new StoredPersonalBest("zulrah", 55.0, "0:55")));

		verify(botClient, never()).send(any(), any(), any());
	}

	@Test
	public void neitherKeepsNorRemembersASnapshotTheBotCouldNotTake()
	{
		when(botClient.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(BotReply.undelivered(null)));

		reporter.reportCaSnapshot(TARGET, "Clanmate", ACCOUNT, List.of(new CompletedAchievement(398, "Into the Den of Giants", "Easy")));

		verify(pendingPostStore, never()).add(any(), any());
		verify(deliveredSnapshots, never()).markDelivered(any(), anyLong(), anyString());
	}
}
