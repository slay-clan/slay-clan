package com.slayclan.config;

import com.google.gson.Gson;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RemoteConfigServiceTest
{
	@Rule
	public final MockWebServer server = new MockWebServer();

	private RemoteConfigService service;

	@Before
	public void setUp()
	{
		service = new RemoteConfigService(new OkHttpClient(), new Gson());
	}

	@Test
	public void storesConfigFromValidResponse() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON));

		refresh();

		assertEquals("SlayClan", service.current().getClanName());
	}

	@Test
	public void keepsPreviousConfigWhenResponseIsInvalid() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON));
		server.enqueue(new MockResponse().setBody("{not json"));

		refresh();
		refresh();

		assertEquals("SlayClan", service.current().getClanName());
	}

	@Test
	public void keepsPreviousConfigOnServerError() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON));
		server.enqueue(new MockResponse().setResponseCode(500));

		refresh();
		refresh();

		assertEquals("SlayClan", service.current().getClanName());
	}

	@Test
	public void clearsConfigWhenUrlIsBlank() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON));
		refresh();

		service.refresh("  ").get(5, TimeUnit.SECONDS);

		assertNull(service.current());
	}

	@Test
	public void reportsFailureWhenResponseIsInvalidAndClearsItOnSuccess() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{not json"));
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON));

		refresh();
		assertTrue(service.lastRefreshFailed());

		refresh();
		assertFalse(service.lastRefreshFailed());
	}

	@Test
	public void reportsFailureOnServerError() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(500));

		refresh();

		assertTrue(service.lastRefreshFailed());
	}

	@Test
	public void reportsFailureForUnparseableUrl() throws Exception
	{
		service.refresh("not a url").get(5, TimeUnit.SECONDS);

		assertTrue(service.lastRefreshFailed());
		assertNull(service.current());
	}

	@Test
	public void blankUrlIsNotAFailure() throws Exception
	{
		service.refresh("  ").get(5, TimeUnit.SECONDS);

		assertFalse(service.lastRefreshFailed());
	}

	@Test
	public void startsWithoutConfig()
	{
		assertNull(service.current());
	}

	@Test
	public void rejectsPlainHttpOutsideLoopback() throws Exception
	{
		service.refresh("http://example.com/config.json").get(5, TimeUnit.SECONDS);

		assertTrue(service.lastRefreshFailed());
		assertNull(service.current());
	}

	@Test
	public void rejectsOversizedBody() throws Exception
	{
		server.enqueue(new MockResponse().setBody(new String(new char[(int) RemoteConfigService.MAX_BODY_BYTES + 1]).replace('\0', ' ')));

		refresh();

		assertTrue(service.lastRefreshFailed());
		assertNull(service.current());
	}

	@Test
	public void dropsOldConfigWhenANewUrlFails() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON));
		server.enqueue(new MockResponse().setResponseCode(404));

		refresh();
		service.refresh(localUrl("/other.json")).get(5, TimeUnit.SECONDS);

		assertTrue(service.lastRefreshFailed());
		assertNull(service.current());
	}

	@Test
	public void slowResponseForAnOldUrlDoesNotReplaceANewerConfig() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON).setBodyDelay(500, TimeUnit.MILLISECONDS));
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON.replace("SlayClan", "NewClan")));

		CompletableFuture<Void> slow = service.refresh(localUrl("/old.json"));
		server.takeRequest();
		service.refresh(localUrl("/new.json")).get(5, TimeUnit.SECONDS);
		slow.get(5, TimeUnit.SECONDS);

		assertEquals("NewClan", service.current().getClanName());
	}

	@Test
	public void responseArrivingAfterTheConfigWasClearedIsIgnored() throws Exception
	{
		server.enqueue(new MockResponse().setBody(TestConfigs.VALID_JSON).setBodyDelay(500, TimeUnit.MILLISECONDS));

		CompletableFuture<Void> slow = service.refresh(localUrl("/config.json"));
		server.takeRequest();
		service.refresh(null).get(5, TimeUnit.SECONDS);
		slow.get(5, TimeUnit.SECONDS);

		assertNull(service.current());
	}

	@Test
	public void rejectsPlainHttpToAHostThatOnlyLooksLikeLoopback() throws Exception
	{
		service.refresh("http://127.example.com/config.json").get(5, TimeUnit.SECONDS);

		assertTrue(service.lastRefreshFailed());
		assertEquals(0, server.getRequestCount());
	}

	private void refresh() throws Exception
	{
		service.refresh(localUrl("/config.json")).get(5, TimeUnit.SECONDS);
	}

	// MockWebServer may report the machine name as host, which the https rule would reject
	private String localUrl(String path)
	{
		return "http://localhost:" + server.getPort() + path;
	}
}
