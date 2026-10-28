package com.slayclan.discord;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PendingPostStoreTest
{
	// Relative, since remove() compares with the real clock and the store drops posts older than a week
	private static final Instant NOW = Instant.now().minusSeconds(60);

	private ConfigManager configManager;
	private PendingPostStore store;
	private final PostPayload payload = new PostPayload("task", "0123456789abcdef", "Clanmate", null, NOW.toString(),
		Map.of("Task", "Clan Raid"), null, false);

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		store = new PendingPostStore(configManager, new Gson());
	}

	@Test
	public void startsEmpty()
	{
		assertTrue(store.load(NOW).isEmpty());
	}

	@Test
	public void roundTripsPendingPosts()
	{
		store.add(payload, NOW);

		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		List<PendingPost> loaded = store.load(NOW);

		assertEquals(1, loaded.size());
		assertEquals("Clanmate", loaded.get(0).getPayload().getAccount());
	}

	@Test
	public void dropsPostsOlderThanAWeek()
	{
		store.add(payload, NOW.minus(PendingPostStore.MAX_AGE).minusSeconds(1));
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		assertTrue(store.load(NOW).isEmpty());
	}

	@Test
	public void keepsOnlyTheNewestPosts()
	{
		String stored = null;
		for (int i = 0; i <= PendingPostStore.MAX_POSTS; i++)
		{
			when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(stored);
			store.add(payload, NOW.plusSeconds(i));
			ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
			verify(configManager, org.mockito.Mockito.atLeastOnce()).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
			stored = saved.getValue();
		}
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(stored);

		List<PendingPost> loaded = store.load(NOW);

		assertEquals(PendingPostStore.MAX_POSTS, loaded.size());
		assertEquals(NOW.plusSeconds(1).toString(), loaded.get(0).getCreatedAt());
	}

	@Test
	public void countsDroppedPostsUntilTheyAreTaken()
	{
		store.add(payload, NOW.minus(PendingPostStore.MAX_AGE).minusSeconds(1));
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		store.load(NOW);

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "1");
		when(configManager.getConfiguration("slayclanmemory", "droppedPosts")).thenReturn("1");
		assertEquals(1, store.takeDroppedCount());
		verify(configManager).unsetConfiguration("slayclanmemory", "droppedPosts");
	}

	@Test
	public void countsUnreadablePendingPostsAsDropped()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn("{not json");

		assertTrue(store.load(NOW).isEmpty());

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "1");
	}

	@Test
	public void estimatesHowManyPostsAnUnreadableQueueHeld()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts"))
			.thenReturn("[{\"createdAt\":\"x\",\"payload\":{}},{\"createdAt\":\"y\",\"payload\":{}},{\"createdAt\"");

		assertTrue(store.load(NOW).isEmpty());

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "3");
	}

	@Test
	public void dropsAndCountsPostWithUnreadableDate()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts"))
			.thenReturn("[{\"createdAt\":\"not a date\",\"payload\":{\"embeds\":[]}}]");

		assertTrue(store.load(NOW).isEmpty());

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "1");
	}

	@Test
	public void keepsTheWholePostForTheRetry()
	{
		store.add(payload, NOW);

		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		assertEquals(payload, store.load(NOW).get(0).getPayload());
	}

	@Test
	public void removesAPostAfterItWasSent()
	{
		store.add(payload, NOW);
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());
		PendingPost pending = store.load(NOW).get(0);

		store.remove(pending);

		verify(configManager).unsetConfiguration("slayclanmemory", "pendingPosts");
	}
}
