package com.slayclan.discord;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PendingPostStoreTest
{
	// Relative, since remove() compares with the real clock and the store drops posts older than a week
	private static final Instant NOW = Instant.now().minusSeconds(60);
	private static final long NO_ACCOUNT = -1;

	private ConfigManager configManager;
	private PendingPostStore store;
	private final PostPayload payload = new PostPayload("task", "0123456789abcdef", "Clanmate", null, NOW.toString(),
		Map.of("Task", "Clan Raid"), null, null);

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		store = new PendingPostStore(configManager, new Gson());
	}

	@Test
	public void startsEmpty()
	{
		assertTrue(store.load(NO_ACCOUNT, NOW).isEmpty());
	}

	@Test
	public void roundTripsPendingPosts()
	{
		store.add(payload, NOW, false);

		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		List<PendingPost> loaded = store.load(NO_ACCOUNT, NOW);

		assertEquals(1, loaded.size());
		assertEquals("Clanmate", loaded.get(0).getPayload().getAccount());
	}

	@Test
	public void dropsPostsOlderThanAWeek()
	{
		store.add(payload, NOW.minus(PendingPostStore.MAX_AGE).minusSeconds(1), false);
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		assertTrue(store.load(NO_ACCOUNT, NOW).isEmpty());
	}

	@Test
	public void keepsOnlyTheNewestPosts()
	{
		String stored = null;
		for (int i = 0; i <= PendingPostStore.MAX_POSTS; i++)
		{
			when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(stored);
			store.add(payload, NOW.plusSeconds(i), false);
			ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
			verify(configManager, org.mockito.Mockito.atLeastOnce()).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
			stored = saved.getValue();
		}
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(stored);

		List<PendingPost> loaded = store.load(NO_ACCOUNT, NOW);

		assertEquals(PendingPostStore.MAX_POSTS, loaded.size());
		assertEquals(NOW.plusSeconds(1).toString(), loaded.get(0).getCreatedAt());
	}

	@Test
	public void aPostAddedToAFullQueueTellsWhichPostItPushedOut()
	{
		String stored = null;
		List<PendingPost> pushedOut = List.of();
		for (int i = 0; i <= PendingPostStore.MAX_POSTS; i++)
		{
			when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(stored);
			pushedOut = store.add(payload, NOW.plusSeconds(i), false);
			ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
			verify(configManager, org.mockito.Mockito.atLeastOnce()).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
			stored = saved.getValue();
		}

		assertEquals(1, pushedOut.size());
		assertEquals(NOW.toString(), pushedOut.get(0).getCreatedAt());
	}

	@Test
	public void countsDroppedPostsUntilTheyAreTaken()
	{
		store.add(payload, NOW.minus(PendingPostStore.MAX_AGE).minusSeconds(1), false);
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		store.load(NO_ACCOUNT, NOW);

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "1");
		when(configManager.getConfiguration("slayclanmemory", "droppedPosts")).thenReturn("1");
		assertEquals(1, store.takeDroppedCount());
		verify(configManager).unsetConfiguration("slayclanmemory", "droppedPosts");
	}

	@Test
	public void countsUnreadablePendingPostsAsDropped()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn("{not json");

		assertTrue(store.load(NO_ACCOUNT, NOW).isEmpty());

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "1");
	}

	@Test
	public void estimatesHowManyPostsAnUnreadableQueueHeld()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts"))
			.thenReturn("[{\"createdAt\":\"x\",\"payload\":{}},{\"createdAt\":\"y\",\"payload\":{}},{\"createdAt\"");

		assertTrue(store.load(NO_ACCOUNT, NOW).isEmpty());

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "3");
	}

	@Test
	public void dropsAndCountsPostWithUnreadableDate()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts"))
			.thenReturn("[{\"createdAt\":\"not a date\",\"payload\":{\"embeds\":[]}}]");

		assertTrue(store.load(NO_ACCOUNT, NOW).isEmpty());

		verify(configManager).setConfiguration("slayclanmemory", "droppedPosts", "1");
	}

	@Test
	public void keepsTheWholePostForTheRetry()
	{
		store.add(payload, NOW, false);

		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		assertEquals(payload, store.load(NO_ACCOUNT, NOW).get(0).getPayload());
	}

	@Test
	public void aKeptPostRemembersThatItsScreenshotWaitsOnDisk()
	{
		store.add(payload, NOW, true);
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());

		assertTrue(store.load(NO_ACCOUNT, NOW).get(0).isScreenshotKept());
	}

	@Test
	public void aPostKeptByAnOlderPluginVersionCountsAsHavingNoScreenshot()
	{
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts"))
			.thenReturn("[{\"createdAt\":\"" + NOW + "\",\"payload\":{\"kind\":\"task\",\"nonce\":\"0123456789abcdef\"}}]");

		assertFalse(store.load(NO_ACCOUNT, NOW).get(0).isScreenshotKept());
	}

	@Test
	public void removesAPostAfterItWasSent()
	{
		store.add(payload, NOW, false);
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("slayclanmemory"), eq("pendingPosts"), saved.capture());
		when(configManager.getConfiguration("slayclanmemory", "pendingPosts")).thenReturn(saved.getValue());
		PendingPost pending = store.load(NO_ACCOUNT, NOW).get(0);

		store.remove(pending);

		verify(configManager).unsetConfiguration("slayclanmemory", "pendingPosts");
	}

	@Test
	public void keepsAPostInTheQueueOfTheGameAccountItWasEarnedOn()
	{
		Map<String, String> profile = profile();

		store.add(postOf("42"), NOW, false);

		assertTrue(profile.containsKey("pendingPosts.42"));
		assertFalse(profile.containsKey("pendingPosts"));
	}

	@Test
	public void aClientRetriesOnlyThePostsOfTheAccountItIsLoggedInWith()
	{
		profile();
		store.add(postOf("42"), NOW, false);
		store.add(postOf("7"), NOW, false);

		List<PendingPost> retried = store.load(42, NOW);

		assertEquals(1, retried.size());
		assertEquals("42", retried.get(0).getPayload().getAccountHash());
	}

	@Test
	public void everyAccountAlsoRetriesPostsKeptWithoutAnAccount()
	{
		profile();
		store.add(payload, NOW, false);
		store.add(postOf("42"), NOW, false);

		List<PendingPost> retried = store.load(42, NOW);

		assertEquals(2, retried.size());
		assertEquals(payload, retried.get(0).getPayload());
	}

	@Test
	public void aSentPostLeavesOnlyTheQueueOfItsAccount()
	{
		Map<String, String> profile = profile();
		store.add(postOf("42"), NOW, false);
		store.add(postOf("7"), NOW, false);

		store.remove(store.load(42, NOW).get(0));

		assertFalse(profile.containsKey("pendingPosts.42"));
		assertTrue(profile.containsKey("pendingPosts.7"));
	}

	@Test
	public void aSentPostKeptBeforeTheQueuesWereSplitLeavesTheSharedQueue()
	{
		Map<String, String> profile = profile();
		profile.put("pendingPosts", new Gson().toJson(List.of(new PendingPost(NOW.toString(), postOf("42")))));

		store.remove(store.load(42, NOW).get(0));

		assertFalse(profile.containsKey("pendingPosts"));
	}

	@Test
	public void aClientAlsoRetriesThePostsKeptBeforeTheQueuesWereSplit()
	{
		Map<String, String> profile = profile();
		profile.put("pendingPosts", new Gson().toJson(List.of(new PendingPost(NOW.toString(), postOf("7")))));

		assertEquals(1, store.load(42, NOW).size());
	}

	@Test
	public void aFailedResendIsCountedSoTheNextOneSaysWhichTryItIs()
	{
		profile();
		store.add(postOf("42"), NOW, false);

		store.countFailedSend(store.load(42, NOW).get(0));

		assertEquals(3, store.load(42, NOW).get(0).nextTry());
	}

	@Test
	public void aPostDeliveredByOneClientLeavesTheQueueEvenWhenAnotherClientCountedAFailedSendMeanwhile()
	{
		Map<String, String> profile = profile();
		store.add(postOf("42"), NOW, false);
		PendingPost loadedByThisClient = store.load(42, NOW).get(0);
		store.countFailedSend(loadedByThisClient);

		store.remove(loadedByThisClient);

		assertFalse(profile.containsKey("pendingPosts.42"));
	}

	@Test
	public void aPostKeptBeforeSendsWereCountedIsResentAsTheSecondTry()
	{
		assertEquals(2, new PendingPost(NOW.toString(), payload, null, false, 0).nextTry());
	}

	private PostPayload postOf(String accountHash)
	{
		return payload.withAccountHash(accountHash).withNonce("0123456789abcd" + accountHash.charAt(0) + "0");
	}

	private Map<String, String> profile()
	{
		Map<String, String> profile = new HashMap<>();
		doAnswer(call -> profile.put(call.getArgument(1), call.getArgument(2)))
			.when(configManager).setConfiguration(eq("slayclanmemory"), anyString(), anyString());
		doAnswer(call -> profile.remove((String) call.getArgument(1)))
			.when(configManager).unsetConfiguration(eq("slayclanmemory"), anyString());
		when(configManager.getConfiguration(eq("slayclanmemory"), anyString()))
			.thenAnswer(call -> profile.get((String) call.getArgument(1)));
		return profile;
	}
}
