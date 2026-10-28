package com.slayclan.config;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

@Slf4j
@Singleton
public class RemoteConfigService
{
	// A real clan config is a few tens of kB; anything bigger is a wrong URL or a hostile host
	static final long MAX_BODY_BYTES = 1_000_000;
	private static final Pattern LOOPBACK_IPV4 = Pattern.compile("127(\\.\\d{1,3}){3}");

	private final OkHttpClient httpClient;
	private final RemoteConfigParser parser;
	private final AtomicReference<RemoteConfig> current = new AtomicReference<>();
	private final AtomicReference<String> currentUrl = new AtomicReference<>();
	private final AtomicBoolean lastRefreshFailed = new AtomicBoolean();
	private final AtomicLong latestRefresh = new AtomicLong();

	@Inject
	public RemoteConfigService(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.parser = new RemoteConfigParser(gson);
	}

	public RemoteConfig current()
	{
		return current.get();
	}

	/**
	 * True when the most recent refresh could not fetch or parse the config, so {@link #current()} is stale or
	 * missing.
	 */
	public boolean lastRefreshFailed()
	{
		return lastRefreshFailed.get();
	}

	/**
	 * A failed refresh keeps the previous config only while the URL is unchanged, so that switching to a broken
	 * URL cannot keep posting to the old bot. Only the most recent refresh may change the config, so a slow
	 * response for an old URL cannot overwrite a newer one.
	 */
	public CompletableFuture<Void> refresh(String url)
	{
		long generation = latestRefresh.incrementAndGet();
		if (url == null || url.trim().isEmpty())
		{
			log.debug("No clan config URL, clearing config");
			setCurrent(null, null);
			lastRefreshFailed.set(false);
			return CompletableFuture.completedFuture(null);
		}
		HttpUrl httpUrl = HttpUrl.parse(url.trim());
		if (httpUrl == null || !isAllowed(httpUrl))
		{
			log.info("Clan config URL is not a valid https URL");
			setCurrent(null, null);
			lastRefreshFailed.set(true);
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<Void> done = new CompletableFuture<>();
		Request request = new Request.Builder().url(httpUrl).build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.info("Failed to fetch clan config", e);
				fail(url, generation);
				done.complete(null);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.info("Clan config request returned {}", response.code());
						fail(url, generation);
						return;
					}
					if (!isAllowed(response.request().url()))
					{
						log.info("Clan config URL redirected to a plain http URL, ignoring it");
						fail(url, generation);
						return;
					}
					BufferedSource source = body.source();
					if (source.request(MAX_BODY_BYTES + 1))
					{
						log.info("Clan config is larger than {} bytes, ignoring it", MAX_BODY_BYTES);
						fail(url, generation);
						return;
					}
					String json = source.readUtf8();
					synchronized (RemoteConfigService.this)
					{
						if (generation != latestRefresh.get())
						{
							return;
						}
						parser.parse(json).ifPresentOrElse(
							parsed ->
							{
								setCurrent(parsed, url);
								lastRefreshFailed.set(false);
							},
							() ->
							{
								log.info("Clan config invalid");
								fail(url, generation);
							});
					}
				}
				finally
				{
					done.complete(null);
				}
			}
		});
		return done;
	}

	// Plain http is only safe where nothing can sit in between, which lets a local test server serve the config
	private static boolean isAllowed(HttpUrl url)
	{
		String host = url.host();
		return url.isHttps() || "localhost".equals(host) || LOOPBACK_IPV4.matcher(host).matches() || "::1".equals(host);
	}

	private synchronized void fail(String url, long generation)
	{
		if (generation != latestRefresh.get())
		{
			return;
		}
		lastRefreshFailed.set(true);
		if (!Objects.equals(url, currentUrl.get()))
		{
			setCurrent(null, null);
		}
	}

	private synchronized void setCurrent(RemoteConfig config, String url)
	{
		current.set(config);
		currentUrl.set(url);
	}
}
