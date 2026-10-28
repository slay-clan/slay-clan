package com.slayclan.activity;

import com.slayclan.discord.BotClient;
import com.slayclan.discord.BotReply;
import com.slayclan.discord.BotTarget;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.PostPayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends activity posts. Events and PBs are kept for retry when the bot cannot take them. Status and snapshots
 * are only sent when their content differs from what the bot last accepted for the account, and are not queued,
 * since the next login or log opening sends a fresh one when needed. What the bot says about a post goes to the
 * chat.
 */
@Slf4j
public class ActivityReporter
{
	private final ActivityFormatter formatter;
	private final BotClient botClient;
	private final PendingPostStore pendingPostStore;
	private final DeliveredSnapshots deliveredSnapshots;
	private final Consumer<String> chat;
	// A second check can run before the bot has answered the first send, so the fingerprint last sent per kind and
	// account counts as delivered meanwhile, and only that one is remembered when the bot answers, however the
	// answers are ordered
	private final Map<String, String> lastSent = new ConcurrentHashMap<>();

	public ActivityReporter(ActivityFormatter formatter, BotClient botClient, PendingPostStore pendingPostStore,
		DeliveredSnapshots deliveredSnapshots, Consumer<String> chat)
	{
		this.formatter = formatter;
		this.botClient = botClient;
		this.pendingPostStore = pendingPostStore;
		this.deliveredSnapshots = deliveredSnapshots;
		this.chat = chat;
	}

	public void reportClog(BotTarget target, String account, String item, int itemId, int quantity, int logSlots)
	{
		sendOrQueue(target, formatter.clogEvent(account, item, itemId, quantity, logSlots, Instant.now()));
	}

	public void reportCa(BotTarget target, String account, String task, int taskId, String tier)
	{
		sendOrQueue(target, formatter.caEvent(account, task, taskId, tier, Instant.now()));
	}

	public void reportStatusIfChanged(BotTarget target, String account, long accountHash, CombatAchievementStatus ca,
		CollectionLogStatus clog)
	{
		String fingerprint = DeliveredSnapshots.fingerprint(
			String.join("|", account, ca.completedText(), ca.tierStatusText(), clog.slotsText(), clog.categoriesText()));
		sendIfChanged(target, PostKind.STATUS, accountHash, fingerprint, formatter.status(account, ca, clog, Instant.now()));
	}

	public void reportCaSnapshot(BotTarget target, String account, long accountHash, List<CompletedAchievement> tasks)
	{
		sendIfChanged(target, PostKind.CA_SNAPSHOT, accountHash, formatter.caSnapshot(account, accountHash, tasks, Instant.now()));
	}

	public void reportClogSnapshot(BotTarget target, String account, long accountHash, List<OwnedItem> items)
	{
		sendIfChanged(target, PostKind.CLOG_SNAPSHOT, accountHash, formatter.clogSnapshot(account, accountHash, items, Instant.now()));
	}

	public void reportPbSnapshot(BotTarget target, String account, long accountHash, List<StoredPersonalBest> bests)
	{
		sendIfChanged(target, PostKind.PB_SNAPSHOT, accountHash, formatter.pbSnapshot(account, accountHash, bests, Instant.now()));
	}

	public void reportPersonalBest(BotTarget target, PostPayload payload, byte[] png)
	{
		botClient.send(target, payload, png).thenAccept(reply -> settle(reply, payload));
	}

	private void sendIfChanged(BotTarget target, PostKind kind, long accountHash, ActivityFormatter.Snapshot snapshot)
	{
		sendIfChanged(target, kind, accountHash, snapshot.getFingerprint(), snapshot.getPayload());
	}

	private void sendIfChanged(BotTarget target, PostKind kind, long accountHash, String fingerprint, PostPayload payload)
	{
		String key = kind + ":" + accountHash;
		if (alreadyDeliveredOrOnItsWay(kind, accountHash, fingerprint, key))
		{
			log.debug("{} unchanged since last delivery or already being sent, not sent", kind);
			return;
		}
		lastSent.put(key, fingerprint);
		botClient.send(target, payload, null).thenAccept(reply ->
		{
			tell(reply);
			if (!reply.isDelivered())
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

	private void sendOrQueue(BotTarget target, PostPayload payload)
	{
		botClient.send(target, payload, null).thenAccept(reply -> settle(reply, payload));
	}

	private void settle(BotReply reply, PostPayload payload)
	{
		tell(reply);
		if (!reply.isDelivered())
		{
			pendingPostStore.add(payload, Instant.now());
		}
	}

	private void tell(BotReply reply)
	{
		if (reply.getMessage() != null)
		{
			chat.accept(reply.getMessage());
		}
	}
}
