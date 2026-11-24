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
		List<PendingPost> posts = new ArrayList<>(loadFresh(KEY, now));
		if (accountHash != UNKNOWN_ACCOUNT)
		{
			posts.addAll(loadFresh(queueKey(String.valueOf(accountHash)), now));
		}
		return posts;
	}

	/** Returns the posts this one pushed out of a full queue, so what else was kept for them can go too. */
	public synchronized List<PendingPost> add(PostPayload payload, Instant createdAt, boolean screenshotKept)
	{
		String key = queueKey(payload.getAccountHash());
		List<PendingPost> posts = new ArrayList<>(loadFresh(key, createdAt));
		posts.add(new PendingPost(createdAt.toString(), payload, screenshotKept));
		int pushedOut = Math.max(0, posts.size() - MAX_POSTS);
		if (pushedOut > 0)
		{
			countDropped(pushedOut);
		}
		save(key, posts.subList(pushedOut, posts.size()));
		return List.copyOf(posts.subList(0, pushedOut));
	}

	/** From the post's own queue and from the shared one, where posts kept before the split carry an account too. */
	public synchronized void remove(PendingPost post)
	{
		String ownQueue = queueKey(post.getPayload().getAccountHash());
		removeFrom(ownQueue, post);
		if (!ownQueue.equals(KEY))
		{
			removeFrom(KEY, post);
		}
	}

	private void removeFrom(String key, PendingPost post)
	{
		List<PendingPost> stored = loadFresh(key, Instant.now());
		List<PendingPost> kept = stored.stream()
			.filter(other -> !isSamePost(other, post))
			.collect(Collectors.toList());
		if (kept.size() < stored.size())
		{
			save(key, kept);
		}
	}

	/** Counts a send that failed again, in whichever queue the post waits in. */
	public synchronized void countFailedSend(PendingPost post)
	{
		String ownQueue = queueKey(post.getPayload().getAccountHash());
		countFailedSendIn(ownQueue, post);
		if (!ownQueue.equals(KEY))
		{
			countFailedSendIn(KEY, post);
		}
	}

	private void countFailedSendIn(String key, PendingPost post)
	{
		List<PendingPost> stored = loadFresh(key, Instant.now());
		if (stored.stream().noneMatch(other -> isSamePost(other, post)))
		{
			return;
		}
		List<PendingPost> counted = stored.stream()
			.map(other -> isSamePost(other, post) ? other.withFailedSends(other.nextTry()) : other)
			.collect(Collectors.toList());
		save(key, counted);
	}

	private List<PendingPost> loadFresh(String key, Instant now)
	{
		List<PendingPost> stored = loadAll(key);
		Instant oldest = now.minus(MAX_AGE);
		List<PendingPost> fresh = stored.stream()
			.filter(post -> isNewerThan(post, oldest))
			.collect(Collectors.toList());
		if (fresh.size() < stored.size())
		{
			countDropped(stored.size() - fresh.size());
			save(key, fresh);
		}
		return fresh;
	}

	// By nonce, since another client on the profile may have counted a failed send on the stored copy meanwhile
	private static boolean isSamePost(PendingPost stored, PendingPost post)
	{
		return stored.getPayload().getNonce().equals(post.getPayload().getNonce());
	}

	private static String queueKey(String accountHash)
	{
		return accountHash == null || accountHash.isEmpty() ? KEY : KEY + "." + accountHash;
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

	private List<PendingPost> loadAll(String key)
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

	private void save(String key, List<PendingPost> posts)
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
}
