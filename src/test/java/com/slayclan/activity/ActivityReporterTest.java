package com.slayclan.activity;

import com.google.gson.Gson;
import com.slayclan.discord.Attachment;
import com.slayclan.discord.DiscordWebhookClient;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.WebhookPayload;
import java.util.List;
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
	private static final String URL = "https://discord.com/api/webhooks/2/def";
	private static final long ACCOUNT = 42L;

	private DiscordWebhookClient webhookClient;
	private PendingPostStore pendingPostStore;
	private DeliveredSnapshots deliveredSnapshots;
	private ActivityReporter reporter;

	@Before
	public void setUp()
	{
		webhookClient = mock(DiscordWebhookClient.class);
		pendingPostStore = mock(PendingPostStore.class);
		deliveredSnapshots = mock(DeliveredSnapshots.class);
		when(webhookClient.send(any(), any(), (Attachment) any())).thenReturn(CompletableFuture.completedFuture(true));
		reporter = new ActivityReporter(new ActivityFormatter(new Gson()), webhookClient, pendingPostStore, deliveredSnapshots);
	}

	@Test
	public void sendsAClogEventWithoutAttachment()
	{
		reporter.reportClog(URL, "Clanmate", "Clanmate", "Vorki", 21992, 1, 141);

		ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(URL), payload.capture(), (Attachment) isNull());
		assertEquals("Vorki", payload.getValue().getEmbeds().get(0).getFields().get(1).getValue());
	}

	@Test
	public void queuesAnEventUnderItsKindWhenDeliveryFails()
	{
		when(webhookClient.send(any(), any(), (Attachment) any())).thenReturn(CompletableFuture.completedFuture(false));

		reporter.reportCa(URL, "Clanmate", "Clanmate", "Into the Den of Giants", 398, "Easy");

		verify(pendingPostStore).add(eq(PostKind.CA), any(), any());
	}

	@Test
	public void queuesAPersonalBestUnderItsKindWhenDeliveryFails()
	{
		when(webhookClient.send(any(), any(), (byte[]) any())).thenReturn(CompletableFuture.completedFuture(false));
		WebhookPayload payload = new ActivityFormatter(new Gson()).currentBossBest("Clanmate", "Clanmate", "Yama", "3:09",
			com.slayclan.trigger.TimedLine.Kind.SINGLE, java.time.Instant.EPOCH);

		reporter.reportPersonalBest(URL, payload, null);

		verify(pendingPostStore).add(eq(PostKind.PB), eq(payload), any());
	}

	@Test
	public void remembersTheNewestStatusWhenDiscordAnswersAnOlderOneLast()
	{
		CompletableFuture<Boolean> first = new CompletableFuture<>();
		CompletableFuture<Boolean> second = new CompletableFuture<>();
		when(webhookClient.send(any(), any(), (Attachment) any())).thenReturn(first, second);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, new CombatAchievementStatus(new int[6], new int[6]), clog);
		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, new CombatAchievementStatus(new int[]{1, 0, 0, 0, 0, 0}, new int[6]), clog);
		second.complete(true);
		first.complete(true);

		ArgumentCaptor<String> remembered = ArgumentCaptor.forClass(String.class);
		verify(deliveredSnapshots, times(1)).markDelivered(eq(PostKind.STATUS), eq(ACCOUNT), remembered.capture());
		assertEquals(DeliveredSnapshots.fingerprint(String.join("|", "Clanmate", "1/0/0/0/0/0", "0/0/0/0/0/0", "10/100", "0/0, 0/0, 0/0, 0/0, 0/0")),
			remembered.getValue());
	}

	@Test
	public void sendsAStatusAgainAfterTheFirstAttemptFailed()
	{
		when(webhookClient.send(any(), any(), (Attachment) any()))
			.thenReturn(CompletableFuture.completedFuture(false), CompletableFuture.completedFuture(true));
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[6], new int[6]);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, ca, clog);
		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, ca, clog);

		verify(webhookClient, times(2)).send(eq(URL), any(), (Attachment) isNull());
		verify(deliveredSnapshots, times(1)).markDelivered(eq(PostKind.STATUS), eq(ACCOUNT), anyString());
	}

	@Test
	public void sendsStatusAndRemembersItAsDelivered()
	{
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[]{1, 0, 0, 0, 0, 0}, new int[6]);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, ca, clog);

		verify(webhookClient).send(eq(URL), any(), (Attachment) isNull());
		verify(deliveredSnapshots).markDelivered(eq(PostKind.STATUS), eq(ACCOUNT), anyString());
		verify(pendingPostStore, never()).add(any(), any(), any());
	}

	@Test
	public void sendsStatusOnceWhenCheckedAgainBeforeDiscordAnswers()
	{
		when(webhookClient.send(any(), any(), (Attachment) any())).thenReturn(new CompletableFuture<>());
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[6], new int[6]);
		CollectionLogStatus clog = new CollectionLogStatus(10, 100, new int[5], new int[5]);

		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, ca, clog);
		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT, ca, clog);

		verify(webhookClient, times(1)).send(eq(URL), any(), (Attachment) isNull());
	}

	@Test
	public void skipsStatusThatDiscordAlreadyHas()
	{
		when(deliveredSnapshots.isDelivered(eq(PostKind.STATUS), anyLong(), anyString())).thenReturn(true);

		reporter.reportStatusIfChanged(URL, "Clanmate", "Clanmate", ACCOUNT,
			new CombatAchievementStatus(new int[6], new int[6]), new CollectionLogStatus(10, 100, new int[5], new int[5]));

		verify(webhookClient, never()).send(any(), any(), (Attachment) any());
	}

	@Test
	public void sendsAChangedSnapshotWithItsAttachmentAndRemembersIt()
	{
		reporter.reportClogSnapshot(URL, "Clanmate", "Clanmate", ACCOUNT, List.of(new OwnedItem(21992, "Vorki", 1)));

		ArgumentCaptor<Attachment> attachment = ArgumentCaptor.forClass(Attachment.class);
		verify(webhookClient).send(eq(URL), any(), attachment.capture());
		assertEquals("SPOILER_clog-snapshot.json", attachment.getValue().getFilename());
		verify(deliveredSnapshots).markDelivered(eq(PostKind.CLOG_SNAPSHOT), eq(ACCOUNT), anyString());
	}

	@Test
	public void skipsASnapshotDiscordAlreadyHas()
	{
		when(deliveredSnapshots.isDelivered(eq(PostKind.PB_SNAPSHOT), anyLong(), anyString())).thenReturn(true);

		reporter.reportPbSnapshot(URL, "Clanmate", "Clanmate", ACCOUNT, List.of(new StoredPersonalBest("zulrah", 55.0, "0:55")));

		verify(webhookClient, never()).send(any(), any(), (Attachment) any());
	}

	@Test
	public void neitherQueuesNorRemembersASnapshotThatFailed()
	{
		when(webhookClient.send(any(), any(), (Attachment) any())).thenReturn(CompletableFuture.completedFuture(false));

		reporter.reportCaSnapshot(URL, "Clanmate", "Clanmate", ACCOUNT, List.of(new CompletedAchievement(398, "Into the Den of Giants", "Easy")));

		verify(pendingPostStore, never()).add(any(), any(), any());
		verify(deliveredSnapshots, never()).markDelivered(any(), anyLong(), anyString());
	}
}
