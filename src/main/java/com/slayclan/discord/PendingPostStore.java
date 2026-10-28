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
 * Posts that could not be delivered, kept in the profile. Several deliveries finish on different threads at
 * once, so every read-modify-write is synchronized. Posts that fall out of the queue are counted so the
 * player can be told to submit them manually.
 */
@Slf4j
@Singleton
public class PendingPostStore
{
	static final int MAX_POSTS = 20;
	static final Duration MAX_AGE = Duration.ofDays(7);
	private static final String KEY = "pendingPosts";
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

	public synchronized List<PendingPost> load(Instant now)
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

	public synchronized void add(PostPayload payload, Instant createdAt)
	{
		List<PendingPost> posts = new ArrayList<>(load(createdAt));
		posts.add(new PendingPost(createdAt.toString(), payload));
		if (posts.size() > MAX_POSTS)
		{
			countDropped(posts.size() - MAX_POSTS);
			posts = posts.subList(posts.size() - MAX_POSTS, posts.size());
		}
		save(posts);
	}

	public synchronized void remove(PendingPost post)
	{
		List<PendingPost> posts = load(Instant.now()).stream()
			.filter(other -> !other.equals(post))
			.collect(Collectors.toList());
		save(posts);
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

	private List<PendingPost> loadAll()
	{
		String stored = configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, KEY);
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
			configManager.unsetConfiguration(SlayClanConfig.MEMORY_GROUP, KEY);
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

	private void save(List<PendingPost> posts)
	{
		if (posts.isEmpty())
		{
			configManager.unsetConfiguration(SlayClanConfig.MEMORY_GROUP, KEY);
		}
		else
		{
			configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, KEY, gson.toJson(posts));
		}
	}
}
