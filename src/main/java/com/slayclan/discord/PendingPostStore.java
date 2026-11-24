package com.slayclan.discord;

import com.google.gson.Gson;
import com.slayclan.SlayClanConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Posts that could not be delivered, kept in the profile with one queue per game account. Several clients can
 * share the profile while playing different accounts, and each must neither send nor overwrite the other's
 * queue. Posts without an account, and those kept before the queues were split, stay in the shared queue that
 * every account retries. Several deliveries finish on different threads at once, so every read-modify-write is
 * synchronized. Posts that fall out of a queue are counted so the player can be told to submit them manually.
 */
@Slf4j
@Singleton
public class PendingPostStore
{
	static final int MAX_POSTS = 20;
	static final Duration MAX_AGE = Duration.ofDays(7);
	private static final String KEY = "pendingPosts";
	private static final long UNKNOWN_ACCOUNT = -1;
	private static final String DROPPED_KEY = "droppedPosts";
	// Estimates how many posts an unreadable queue held
	private static final String CREATED_AT_FIELD = "\"createdAt\"";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public PendingPostStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** The shared queue first, then the account's own; an unknown account (-1) gets the shared queue only. */
	public synchronized List<PendingPost> load(long accountHash, Instant now)
	{
		List<PendingPost> posts = new ArrayList<>(shared().loadFresh(now));
		if (accountHash != UNKNOWN_ACCOUNT)
		{
			posts.addAll(queueOf(String.valueOf(accountHash)).loadFresh(now));
		}
		return posts;
	}

	/** Returns the posts this one pushed out of a full queue, so what else was kept for them can go too. */
	public synchronized List<PendingPost> add(PostPayload payload, Instant createdAt, boolean screenshotKept)
	{
		Queue queue = queueOf(payload.getAccountHash());
		List<PendingPost> posts = new ArrayList<>(queue.loadFresh(createdAt));
		posts.add(new PendingPost(createdAt.toString(), payload, screenshotKept));
		int pushedOut = Math.max(0, posts.size() - MAX_POSTS);
		if (pushedOut > 0)
		{
			countDropped(pushedOut);
		}
		queue.save(posts.subList(pushedOut, posts.size()));
		return List.copyOf(posts.subList(0, pushedOut));
	}

	public synchronized void remove(PendingPost post)
	{
		queuesHolding(post).forEach(queue -> queue.remove(post));
	}

	/** Counts a send that failed again, in whichever queue the post waits in. */
	public synchronized void countFailedSend(PendingPost post)
	{
		queuesHolding(post).forEach(queue -> queue.countFailedSend(post));
	}

	// Posts kept before the queues were split carry an account too, but wait in the shared queue
	private List<Queue> queuesHolding(PendingPost post)
	{
		Queue own = queueOf(post.getPayload().getAccountHash());
		return own.isShared() ? List.of(own) : List.of(own, shared());
	}

	private Queue queueOf(String accountHash)
	{
		return hasNoAccount(accountHash) ? shared() : new Queue(KEY + "." + accountHash);
	}

	private Queue shared()
	{
		return new Queue(KEY);
	}

	private static boolean hasNoAccount(String accountHash)
	{
		return accountHash == null || accountHash.isEmpty();
	}

	// By nonce, since another client on the profile may have counted a failed send on the stored copy meanwhile
	private static boolean isSamePost(PendingPost stored, PendingPost post)
	{
		return stored.getPayload().getNonce().equals(post.getPayload().getNonce());
	}

	/**
	 * Returns how many posts were dropped since the last call and resets the count.
	 */
	public synchronized int takeDroppedCount()
	{
		int dropped = droppedCount();
		configManager.unsetConfiguration(SlayClanConfig.MEMORY_GROUP, DROPPED_KEY);
		return dropped;
	}

	private int droppedCount()
	{
		String stored = configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, DROPPED_KEY);
		try
		{
			return stored == null ? 0 : Integer.parseInt(stored);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/**
	 * A post whose date cannot be read is treated as expired, so it is dropped and counted.
	 */
	private static boolean isNewerThan(PendingPost post, Instant oldest)
	{
		try
		{
			return post != null && post.getPayload() != null && Instant.parse(post.getCreatedAt()).isAfter(oldest);
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private static int occurrences(String text, String part)
	{
		int count = 0;
		for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length()))
		{
			count++;
		}
		return count;
	}

	private void countDropped(int count)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, DROPPED_KEY, String.valueOf(droppedCount() + count));
	}

	/** One queue in the profile, under its own key. */
	private final class Queue
	{
		private final String key;

		Queue(String key)
		{
			this.key = key;
		}

		boolean isShared()
		{
			return KEY.equals(key);
		}

		List<PendingPost> loadFresh(Instant now)
		{
			List<PendingPost> stored = loadAll();
			Instant oldest = now.minus(MAX_AGE);
			List<PendingPost> fresh = stored.stream()
				.filter(post -> isNewerThan(post, oldest))
				.collect(Collectors.toList());
			if (fresh.size() < stored.size())
			{
				countDropped(stored.size() - fresh.size());
				save(fresh);
			}
			return fresh;
		}

		void remove(PendingPost post)
		{
			List<PendingPost> stored = loadFresh(Instant.now());
			List<PendingPost> kept = stored.stream()
				.filter(other -> !isSamePost(other, post))
				.collect(Collectors.toList());
			if (kept.size() < stored.size())
			{
				save(kept);
			}
		}

		void countFailedSend(PendingPost post)
		{
			List<PendingPost> stored = loadFresh(Instant.now());
			if (stored.stream().noneMatch(other -> isSamePost(other, post)))
			{
				return;
			}
			save(stored.stream()
				.map(other -> isSamePost(other, post) ? other.withFailedSends(other.nextTry()) : other)
				.collect(Collectors.toList()));
		}

		void save(List<PendingPost> posts)
		{
			if (posts.isEmpty())
			{
				configManager.unsetConfiguration(SlayClanConfig.MEMORY_GROUP, key);
			}
			else
			{
				configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, key, gson.toJson(posts));
			}
		}

		private List<PendingPost> loadAll()
		{
			String stored = configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, key);
			if (stored == null || stored.isEmpty())
			{
				return Collections.emptyList();
			}
			try
			{
				return Arrays.asList(gson.fromJson(stored, PendingPost[].class));
			}
			catch (RuntimeException e)
			{
				log.warn("Discarding unreadable pending posts", e);
				configManager.unsetConfiguration(SlayClanConfig.MEMORY_GROUP, key);
				countDropped(Math.max(1, occurrences(stored, CREATED_AT_FIELD)));
				return Collections.emptyList();
			}
		}
	}
}
