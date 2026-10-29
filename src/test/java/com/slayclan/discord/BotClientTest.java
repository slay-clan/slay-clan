package com.slayclan.discord;

import com.google.gson.Gson;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class BotClientTest
{
	private static final String TOKEN = "sc_test-token";

	@Rule
	public final MockWebServer server = new MockWebServer();

	private final PostPayload task = new PostPayload("task", "0123456789abcdef", "Clanmate", null, "2026-10-10T18:00:00Z",
		Map.of("Task", "Clan Raid", "Tier", "Repeatable"), null, false);
	private final PostPayload clog = new PostPayload("clog", "0123456789abcdef", "Clanmate", null, "2026-10-10T18:00:00Z",
		Map.of("Item", "Vorki"), null, false);
	private final PostPayload currentBest = new PostPayload("pb", "0123456789abcdef", "Clanmate", null, "2026-10-10T18:00:00Z",
		Map.of("Boss", "Zulrah", "New", "no"), null, true);

	private ScheduledExecutorService executor;
	private BotClient client;

	@Before
	public void setUp()
	{
		executor = Executors.newSingleThreadScheduledExecutor();
		client = new BotClient(new OkHttpClient(), new Gson(), executor);
	}

	@After
	public void tearDown()
	{
		executor.shutdownNow();
	}

	@Test
	public void sendsThePostAndTheScreenshotAsMultipartWithTheToken() throws Exception
	{
		server.enqueue(recorded());

		BotReply reply = send(task, new byte[]{1, 2, 3});

		assertTrue(reply.isDelivered());
		RecordedRequest request = server.takeRequest();
		String body = request.getBody().readUtf8();
		assertEquals("POST", request.getMethod());
		assertEquals("Bearer " + TOKEN, request.getHeader("Authorization"));
		assertTrue(request.getHeader("Content-Type").startsWith("multipart/form-data"));
		assertTrue(body.contains("name=\"post\""));
		assertTrue(body.contains("\"kind\":\"task\""));
		assertTrue(body.contains("\"Task\":\"Clan Raid\""));
		assertTrue(body.contains("name=\"screenshot\"; filename=\"screenshot.png\""));
	}

	@Test
	public void sendsWithoutScreenshotPartWhenThereIsNone() throws Exception
	{
		server.enqueue(recorded());

		send(task, null);

		assertFalse(server.takeRequest().getBody().readUtf8().contains("filename="));
	}

	@Test
	public void passesTheBotsAnswerOnForTheChat() throws Exception
	{
		server.enqueue(json(200, "{\"status\":\"refused\",\"message\":\"Not recorded: already completed.\"}"));

		assertEquals("Not recorded: already completed.", send(task, null).getMessage());
	}

	@Test
	public void saysNothingAboutADuplicate() throws Exception
	{
		server.enqueue(json(200, "{\"status\":\"duplicate\",\"message\":\"Already recorded.\"}"));

		assertNull(send(task, null).getMessage());
	}

	@Test
	public void saysNothingAboutARecordedFeedPost() throws Exception
	{
		server.enqueue(recorded());

		assertNull(send(clog, null).getMessage());
	}

	@Test
	public void saysNothingAboutARecordedQuietPost() throws Exception
	{
		server.enqueue(recorded());

		assertNull(send(currentBest, null).getMessage());
		assertFalse(server.takeRequest().getBody().readUtf8().contains("quiet"));
	}

	@Test
	public void saysWhenAFeedPostWasRefused() throws Exception
	{
		server.enqueue(json(200, "{\"status\":\"refused\",\"message\":\"Not recorded: that account is not yours.\"}"));

		assertEquals("Not recorded: that account is not yours.", send(clog, null).getMessage());
	}

	@Test
	public void keepsThePostAndTellsTheMemberWhenTheTokenIsRejected() throws Exception
	{
		server.enqueue(json(401, "{\"status\":\"error\",\"message\":\"Token not accepted.\"}"));

		BotReply reply = send(task, null);

		assertFalse(reply.isDelivered());
		assertTrue(reply.isTokenRejected());
		assertTrue(reply.getMessage().contains("/plugin token"));
	}

	@Test
	public void keepsThePostQuietlyWhenTheAddressIsWrong() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(404));

		BotReply reply = send(task, null);

		assertFalse(reply.isDelivered());
		assertNull(reply.getMessage());
	}

	@Test
	public void sendsAgainWithoutTheScreenshotWhenItIsTooLarge() throws Exception
	{
		server.enqueue(json(413, "{\"status\":\"error\",\"message\":\"The post is too large.\"}"));
		server.enqueue(recorded());

		assertTrue(send(task, new byte[]{1, 2, 3}).isDelivered());
		assertEquals(2, server.getRequestCount());
		server.takeRequest();
		assertFalse(server.takeRequest().getBody().readUtf8().contains("filename="));
	}

	@Test
	public void retriesOnceAfterRateLimit() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));
		server.enqueue(recorded());

		assertTrue(send(task, null).isDelivered());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void givesUpAfterSecondRateLimit() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));

		assertFalse(send(task, null).isDelivered());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void cancelledRetryReportsFailureSoThePostIsKept() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "30"));

		CompletableFuture<BotReply> result = client.send(target(), task, null);
		server.takeRequest();
		long deadline = System.currentTimeMillis() + 5_000;
		while (client.pendingRetryCount() == 0 && System.currentTimeMillis() < deadline)
		{
			Thread.yield();
		}
		client.cancelRetries();

		assertFalse(result.get(5, TimeUnit.SECONDS).isDelivered());
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void reportsFailureOnServerErrorWithoutRetry() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(500));

		assertFalse(send(task, null).isDelivered());
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void aPostTheBotCannotReadIsNotKeptAndItsReasonIsSaidInChat() throws Exception
	{
		server.enqueue(json(400, "{\"status\":\"error\",\"message\":\"The nonce must be 16 hex characters.\"}"));

		BotReply reply = send(task, null);

		assertTrue(reply.isDelivered());
		assertEquals("The nonce must be 16 hex characters.", reply.getMessage());
	}

	private BotReply send(PostPayload payload, byte[] png) throws Exception
	{
		return client.send(target(), payload, png).get(5, TimeUnit.SECONDS);
	}

	private BotTarget target()
	{
		return new BotTarget(server.url("/api/v1/posts").toString(), TOKEN);
	}

	private static MockResponse recorded()
	{
		return json(200, "{\"status\":\"recorded\",\"message\":\"Recorded.\",\"warnings\":[]}");
	}

	private static MockResponse json(int code, String body)
	{
		return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
	}
}
