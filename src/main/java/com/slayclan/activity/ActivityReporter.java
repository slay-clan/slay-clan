package com.slayclan.activity;

import com.slayclan.discord.BotClient;
import com.slayclan.discord.BotReply;
import com.slayclan.discord.BotTarget;
import com.slayclan.discord.PendingPost;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.PostPayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends posts to the bot and settles what comes back: the answer goes to the chat, a post the bot could not
 * take is kept for retry. Status and snapshots are only sent when their content differs from what the bot
 * last accepted for the account, and are not kept, since the next login or log opening sends a fresh one when
 * needed.
 */
@Slf4j
public class ActivityReporter
{
	private final ActivityFormatter formatter;
	private final BotClient botClient;
	private final PendingPostStore pendingPostStore;
	private final DeliveredSnapshots deliveredSnapshots;
	private final Consumer<BotReply> tell;
	// A second check can run before the bot has answered the first send, so the fingerprint last sent per kind and
	// account counts as delivered meanwhile, and only that one is remembered when the bot answers, however the
	// answers are ordered
	private final Map<String, String> lastSent = new ConcurrentHashMap<>();

	public ActivityReporter(ActivityFormatter formatter, BotClient botClient, PendingPostStore pendingPostStore,
		DeliveredSnapshots deliveredSnapshots, Consumer<BotReply> tell)
	{
		this.formatter = formatter;
		this.botClient = botClient;
		this.pendingPostStore = pendingPostStore;
		this.deliveredSnapshots = deliveredSnapshots;
		this.tell = tell;
	}

	/**
	 * Sends a post the member just earned and keeps it when the bot could not take it.
	 */
	public CompletableFuture<Void> send(BotTarget target, PostPayload payload, byte[] png)
	{
		return botClient.send(target, payload, png).thenAccept(reply -> settle(reply, payload));
	}

	/**
	 * Sends kept posts one at a time, dropping each the bot took. The target is read again for every post, so a
	 * Clan bot URL changed during the round is followed. Stops at a blocked post, since every post after it would
	 * fail the same way.
	 */
	public CompletableFuture<Void> retry(Supplier<BotTarget> target, List<PendingPost> pending)
	{
		if (pending.isEmpty())
		{
			return CompletableFuture.completedFuture(null);
		}
		PendingPost first = pending.get(0);
		return botClient.send(target.get(), first.getPayload(), null).thenCompose(reply ->
		{
			tell.accept(reply);
			if (reply.isDelivered())
			{
				pendingPostStore.remove(first);
			}
			if (reply.isBlocked())
			{
				return CompletableFuture.completedFuture(null);
			}
			return retry(target, pending.subList(1, pending.size()));
		});
	}

	public void reportClog(BotTarget target, String account, String item, int itemId, int quantity, int logSlots)
	{
		send(target, formatter.clogEvent(account, item, itemId, quantity, logSlots, Instant.now()), null);
	}

	public void reportCa(BotTarget target, String account, String task, int taskId, String tier)
	{
		send(target, formatter.caEvent(account, task, taskId, tier, Instant.now()), null);
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
			tell.accept(reply);
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

	private void settle(BotReply reply, PostPayload payload)
	{
		tell.accept(reply);
		if (!reply.isDelivered())
		{
			log.info("Keeping an undelivered {} post for retry", payload.getKind());
			pendingPostStore.add(payload, Instant.now());
		}
	}
}
