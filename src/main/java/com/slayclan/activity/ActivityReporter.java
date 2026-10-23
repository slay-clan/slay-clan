package com.slayclan.activity;

import com.slayclan.discord.Attachment;
import com.slayclan.discord.DiscordWebhookClient;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.WebhookPayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends activity posts. Events and PBs are kept for retry when Discord cannot be reached. Status and snapshots are
 * only sent when their content differs from what Discord last accepted for the account, and are not queued, since
 * the next login or log opening sends a fresh one when needed.
 */
@Slf4j
public class ActivityReporter
{
	private final ActivityFormatter formatter;
	private final DiscordWebhookClient webhookClient;
	private final PendingPostStore pendingPostStore;
	private final DeliveredSnapshots deliveredSnapshots;
	// A second check can run before Discord has answered the first send, so the fingerprint last sent per kind and
	// account counts as delivered meanwhile, and only that one is remembered when Discord answers, however the
	// answers are ordered
	private final Map<String, String> lastSent = new ConcurrentHashMap<>();

	public ActivityReporter(ActivityFormatter formatter, DiscordWebhookClient webhookClient, PendingPostStore pendingPostStore,
		DeliveredSnapshots deliveredSnapshots)
	{
		this.formatter = formatter;
		this.webhookClient = webhookClient;
		this.pendingPostStore = pendingPostStore;
		this.deliveredSnapshots = deliveredSnapshots;
	}

	public void reportClog(String webhookUrl, String discordName, String account, String item, int itemId, int quantity, int logSlots)
	{
		sendOrQueue(webhookUrl, PostKind.CLOG, formatter.clogEvent(discordName, account, item, itemId, quantity, logSlots, Instant.now()));
	}

	public void reportCa(String webhookUrl, String discordName, String account, String task, int taskId, String tier)
	{
		sendOrQueue(webhookUrl, PostKind.CA, formatter.caEvent(discordName, account, task, taskId, tier, Instant.now()));
	}

	public void reportStatusIfChanged(String webhookUrl, String discordName, String account, long accountHash,
		CombatAchievementStatus ca, CollectionLogStatus clog)
	{
		String fingerprint = DeliveredSnapshots.fingerprint(
			String.join("|", account, ca.completedText(), ca.tierStatusText(), clog.slotsText(), clog.categoriesText()));
		sendIfChanged(webhookUrl, PostKind.STATUS, accountHash, fingerprint,
			formatter.status(discordName, account, ca, clog, Instant.now()), null);
	}

	public void reportCaSnapshot(String webhookUrl, String discordName, String account, long accountHash, List<CompletedAchievement> tasks)
	{
		sendIfChanged(webhookUrl, PostKind.CA_SNAPSHOT, accountHash, formatter.caSnapshot(discordName, account, accountHash, tasks, Instant.now()));
	}

	public void reportClogSnapshot(String webhookUrl, String discordName, String account, long accountHash, List<OwnedItem> items)
	{
		sendIfChanged(webhookUrl, PostKind.CLOG_SNAPSHOT, accountHash, formatter.clogSnapshot(discordName, account, accountHash, items, Instant.now()));
	}

	public void reportPbSnapshot(String webhookUrl, String discordName, String account, long accountHash, List<StoredPersonalBest> bests)
	{
		sendIfChanged(webhookUrl, PostKind.PB_SNAPSHOT, accountHash, formatter.pbSnapshot(discordName, account, accountHash, bests, Instant.now()));
	}

	public void reportPersonalBest(String webhookUrl, WebhookPayload payload, byte[] png)
	{
		webhookClient.send(webhookUrl, payload, png).thenAccept(sent -> keepIfUndelivered(sent, PostKind.PB, payload));
	}

	private void sendIfChanged(String webhookUrl, PostKind kind, long accountHash, ActivityFormatter.Snapshot snapshot)
	{
		sendIfChanged(webhookUrl, kind, accountHash, snapshot.getFingerprint(), snapshot.getPayload(), snapshot.getAttachment());
	}

	private void sendIfChanged(String webhookUrl, PostKind kind, long accountHash, String fingerprint, WebhookPayload payload,
		Attachment attachment)
	{
		String key = kind + ":" + accountHash;
		if (alreadyDeliveredOrOnItsWay(kind, accountHash, fingerprint, key))
		{
			log.debug("{} unchanged since last delivery or already being sent, not sent", kind);
			return;
		}
		lastSent.put(key, fingerprint);
		webhookClient.send(webhookUrl, payload, attachment).thenAccept(sent ->
		{
			if (!sent)
			{
				log.info("{} not delivered, it is sent again next time", kind);
				lastSent.remove(key, fingerprint);
			}
			else if (lastSent.remove(key, fingerprint))
			{
				deliveredSnapshots.markDelivered(kind, accountHash, fingerprint);
			}
		});
	}

	private boolean alreadyDeliveredOrOnItsWay(PostKind kind, long accountHash, String fingerprint, String key)
	{
		return deliveredSnapshots.isDelivered(kind, accountHash, fingerprint) || fingerprint.equals(lastSent.get(key));
	}

	private void sendOrQueue(String webhookUrl, PostKind kind, WebhookPayload payload)
	{
		webhookClient.send(webhookUrl, payload, (Attachment) null).thenAccept(sent -> keepIfUndelivered(sent, kind, payload));
	}

	private void keepIfUndelivered(boolean sent, PostKind kind, WebhookPayload payload)
	{
		if (!sent)
		{
			pendingPostStore.add(kind, payload, Instant.now());
		}
	}
}
