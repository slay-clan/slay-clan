package com.slayclan.activity;

import com.slayclan.PluginVersion;
import com.slayclan.discord.BotClient;
import com.slayclan.discord.BotReply;
import com.slayclan.discord.BotTarget;
import com.slayclan.discord.KeptScreenshots;
import com.slayclan.discord.PendingPost;
import com.slayclan.discord.PendingPostStore;
import com.slayclan.discord.PostProblems;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.PostPayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends posts to the bot and settles what comes back: the answer goes to the chat, a post the bot could not
 * take is kept for retry, its screenshot on disk until the bot has answered. Status and snapshots are only sent
 * when their content differs from what the bot last recorded for the account, and are not kept, since the next
 * login or log opening sends a fresh one when needed; a refused one is sent again then too. A post that
 * completes a task reports the bot's answer back, so a task the bot refused is not left looking done.
 */
@Slf4j
public class ActivityReporter
{
	private final ActivityFormatter formatter;
	private final BotClient botClient;
	private final PendingPostStore pendingPostStore;
	private final KeptScreenshots keptScreenshots;
	private final DeliveredSnapshots deliveredSnapshots;
	private final BotAnswers answers;
	// Screenshot files are only read and written here, since a retry round starts on the client thread
	private final Executor executor;
	// A second check can run before the bot has answered the first send, so the fingerprint last sent per kind and
	// account counts as delivered meanwhile, and only that one is remembered when the bot answers, however the
	// answers are ordered
	private final Map<String, String> lastSent = new ConcurrentHashMap<>();

	public ActivityReporter(ActivityFormatter formatter, BotClient botClient, PendingPostStore pendingPostStore,
		KeptScreenshots keptScreenshots, DeliveredSnapshots deliveredSnapshots, BotAnswers answers, Executor executor)
	{
		this.formatter = formatter;
		this.botClient = botClient;
		this.pendingPostStore = pendingPostStore;
		this.keptScreenshots = keptScreenshots;
		this.deliveredSnapshots = deliveredSnapshots;
		this.answers = answers;
		this.executor = executor;
	}

	/**
	 * Sends a post the member just earned and keeps it when the bot could not take it.
	 */
	public CompletableFuture<Void> send(BotTarget target, PostPayload payload, byte[] png)
	{
		return botClient.send(target, payload, png).thenAccept(reply -> settle(reply, payload, png));
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
		return keptScreenshotOf(first)
			.thenCompose(png -> botClient.send(target.get(), resent(first, png), png))
			.thenCompose(reply ->
			{
				answers.tell(reply, first.getPayload().postKind());
				if (reply.isDelivered())
				{
					pendingPostStore.remove(first);
					discardScreenshotOf(first);
					settleTask(first.getTaskId(), reply);
				}
				else
				{
					pendingPostStore.countFailedSend(first);
				}
				if (reply.isBlocked())
				{
					return CompletableFuture.completedFuture(null);
				}
				return retry(target, pending.subList(1, pending.size()));
			});
	}

	// Staff see on the review post that it came late, and why it has no screenshot when the kept one was gone
	private static PostPayload resent(PendingPost post, byte[] png)
	{
		PostPayload payload = PostProblems.with(post.getPayload(), PostProblems.sentOnTry(post.nextTry()));
		return screenshotLost(post, png) ? PostProblems.with(payload, PostProblems.SCREENSHOT_LOST) : payload;
	}

	private static boolean screenshotLost(PendingPost post, byte[] png)
	{
		return post.isScreenshotKept() && png == null;
	}

	private CompletableFuture<byte[]> keptScreenshotOf(PendingPost post)
	{
		if (!post.isScreenshotKept())
		{
			return CompletableFuture.completedFuture(null);
		}
		return CompletableFuture.supplyAsync(() -> keptScreenshots.load(post.getPayload().getNonce()), executor);
	}

	private void keepScreenshot(String nonce, byte[] png)
	{
		keptScreenshots.keep(nonce, png);
		keptScreenshots.prune(Instant.now());
	}

	private void discardScreenshotOf(PendingPost post)
	{
		if (post.isScreenshotKept())
		{
			executor.execute(() -> keptScreenshots.discard(post.getPayload().getNonce()));
		}
	}

	public void reportClog(BotTarget target, Account account, String item, int itemId, int quantity, int logSlots)
	{
		send(target, account.stamp(formatter.clogEvent(account.getName(), item, itemId, quantity, logSlots, Instant.now())), null);
	}

	public void reportCa(BotTarget target, Account account, String task, int taskId, String tier)
	{
		send(target, account.stamp(formatter.caEvent(account.getName(), task, taskId, tier, Instant.now())), null);
	}

	public void reportStatusIfChanged(BotTarget target, String account, long accountHash, CombatAchievementStatus ca,
		CollectionLogStatus clog)
	{
		String fingerprint = DeliveredSnapshots.fingerprint(
			String.join("|", account, ca.completedText(), ca.tierStatusText(), clog.slotsText(), clog.categoriesText(),
				PluginVersion.VERSION));
		sendIfChanged(target, PostKind.STATUS, accountHash, fingerprint, formatter.status(account, ca, clog, Instant.now()));
	}

	public void reportCaSnapshot(BotTarget target, String account, long accountHash, List<CompletedAchievement> tasks)
	{
		sendIfChanged(target, PostKind.CA_SNAPSHOT, accountHash, formatter.caSnapshot(account, accountHash, tasks, Instant.now()));
	}

	public void reportClogSnapshot(BotTarget target, String account, long accountHash, CapturedLog captured)
	{
		sendIfChanged(target, PostKind.CLOG_SNAPSHOT, accountHash, formatter.clogSnapshot(account, accountHash, captured, Instant.now()));
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
			answers.tell(reply, kind);
			if (!reply.isRecorded())
			{
				log.info("{} not recorded, it is sent again next time", kind);
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

	private void settle(BotReply reply, PostPayload payload, byte[] png)
	{
		answers.tell(reply, payload.postKind());
		if (!reply.isDelivered())
		{
			log.info("Keeping an undelivered {} post for retry", payload.getKind());
			// Queued before the post is stored, so a retry round that loads the post reads the file after it is written
			if (png != null)
			{
				executor.execute(() -> keepScreenshot(payload.getNonce(), png));
			}
			pendingPostStore.add(payload, Instant.now(), png != null).forEach(this::discardScreenshotOf);
			return;
		}
		settleTask(payload.getTaskId(), reply);
	}

	private void settleTask(String taskId, BotReply reply)
	{
		if (taskId != null)
		{
			answers.taskAnswered(taskId, reply);
		}
	}
}
