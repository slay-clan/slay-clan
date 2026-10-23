package com.slayclan.discord;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DiscordWebhookClientTest
{
	@Rule
	public final MockWebServer server = new MockWebServer();

	private final WebhookPayload payload = new WebhookPayload(List.of(
		new WebhookPayload.Embed("Clanmate and Raidbuddy", "Has completed the following clan task", 1, "2026-10-10T18:00:00Z", null, List.of(), null)));

	private ScheduledExecutorService executor;
	private DiscordWebhookClient client;

	@Before
	public void setUp()
	{
		executor = Executors.newSingleThreadScheduledExecutor();
		client = new DiscordWebhookClient(new OkHttpClient(), new Gson(), executor);
	}

	@After
	public void tearDown()
	{
		executor.shutdownNow();
	}

	@Test
	public void sendsPayloadAndScreenshotAsMultipart() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(204));

		assertTrue(send(new byte[]{1, 2, 3}));

		RecordedRequest request = server.takeRequest();
		String body = request.getBody().readUtf8();
		assertEquals("POST", request.getMethod());
		assertTrue(request.getHeader("Content-Type").startsWith("multipart/form-data"));
		assertTrue(body.contains("name=\"payload_json\""));
		assertTrue(body.contains("Clanmate and Raidbuddy"));
		assertTrue(body.contains("filename=\"screenshot.png\""));
	}

	@Test
	public void sendsJsonAttachmentUnderItsOwnName() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(204));

		boolean sent = client.send(server.url("/api/webhooks/1/abc").toString(), payload,
			Attachment.json("clog-snapshot.json", "{\"items\":[]}")).get(5, TimeUnit.SECONDS);

		assertTrue(sent);
		String body = server.takeRequest().getBody().readUtf8();
		assertTrue(body.contains("filename=\"clog-snapshot.json\""));
		assertTrue(body.contains("Content-Type: application/json"));
		assertTrue(body.contains("{\"items\":[]}"));
	}

	@Test
	public void sendsWithoutFilePartWhenScreenshotMissing() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(204));

		assertTrue(send(null));

		assertFalse(server.takeRequest().getBody().readUtf8().contains("filename="));
	}

	@Test
	public void retriesOnceAfterRateLimit() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));
		server.enqueue(new MockResponse().setResponseCode(204));

		assertTrue(send(null));
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void givesUpAfterSecondRateLimit() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));

		assertFalse(send(null));
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void cancelledRetryReportsFailureSoThePostIsKept() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "30"));

		java.util.concurrent.CompletableFuture<Boolean> result = client.send(server.url("/api/webhooks/1/abc").toString(), payload, (byte[]) null);
		server.takeRequest();
		long deadline = System.currentTimeMillis() + 5_000;
		while (client.pendingRetryCount() == 0 && System.currentTimeMillis() < deadline)
		{
			Thread.yield();
		}
		client.cancelRetries();

		assertFalse(result.get(5, TimeUnit.SECONDS));
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void reportsFailureOnServerErrorWithoutRetry() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(500));

		assertFalse(send(null));
		assertEquals(1, server.getRequestCount());
	}

	private boolean send(byte[] png) throws Exception
	{
		return client.send(server.url("/api/webhooks/1/abc").toString(), payload, png).get(5, TimeUnit.SECONDS);
	}
}
