package com.slayclan.discord;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class DiscordWebhookClient
{
	private static final long DEFAULT_RETRY_MILLIS = 1_000;
	// Discord webhook limits reset within seconds; anything longer is left to the pending post retry
	private static final long MAX_RETRY_MILLIS = 60_000;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Map<ScheduledFuture<?>, CompletableFuture<Boolean>> scheduledRetries = new ConcurrentHashMap<>();

	@Inject
	public DiscordWebhookClient(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.executor = executor;
	}

	public CompletableFuture<Boolean> send(String webhookUrl, WebhookPayload payload, byte[] png)
	{
		return send(webhookUrl, payload, png == null ? null : Attachment.png(png));
	}

	public CompletableFuture<Boolean> send(String webhookUrl, WebhookPayload payload, Attachment attachment)
	{
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		post(webhookUrl, buildBody(payload, attachment), true, result);
		return result;
	}

	/**
	 * Drops rate limit retries that have not run yet. Their futures complete as failed, so the posts are kept.
	 */
	public void cancelRetries()
	{
		for (Map.Entry<ScheduledFuture<?>, CompletableFuture<Boolean>> retry : scheduledRetries.entrySet())
		{
			if (retry.getKey().cancel(false))
			{
				retry.getValue().complete(false);
			}
		}
		scheduledRetries.clear();
	}

	int pendingRetryCount()
	{
		return scheduledRetries.size();
	}

	private RequestBody buildBody(WebhookPayload payload, Attachment attachment)
	{
		MultipartBody.Builder builder = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("payload_json", gson.toJson(payload));
		if (attachment != null)
		{
			builder.addFormDataPart("file", attachment.getFilename(),
				RequestBody.create(MediaType.parse(attachment.getMediaType()), attachment.getBytes()));
		}
		return builder.build();
	}

	private void post(String webhookUrl, RequestBody body, boolean retryOnRateLimit, CompletableFuture<Boolean> result)
	{
		Request request = new Request.Builder().url(webhookUrl).post(body).build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to post clan task to Discord", e);
				result.complete(false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (response.code() == 429 && retryOnRateLimit)
					{
						scheduleRetry(webhookUrl, body, result, retryAfterMillis(response));
						return;
					}
					if (!response.isSuccessful())
					{
						log.warn("Discord rejected clan task post with status {}", response.code());
					}
					result.complete(response.isSuccessful());
				}
			}
		});
	}

	private void scheduleRetry(String webhookUrl, RequestBody body, CompletableFuture<Boolean> result, long delayMillis)
	{
		ScheduledFuture<?> retry = executor.schedule(() -> post(webhookUrl, body, false, result), delayMillis, TimeUnit.MILLISECONDS);
		scheduledRetries.put(retry, result);
		result.whenComplete((sent, error) -> scheduledRetries.remove(retry));
	}

	private static long retryAfterMillis(Response response)
	{
		String header = response.header("Retry-After");
		if (header == null)
		{
			return DEFAULT_RETRY_MILLIS;
		}
		try
		{
			return Math.min(MAX_RETRY_MILLIS, (long) (Double.parseDouble(header) * 1000));
		}
		catch (NumberFormatException e)
		{
			return DEFAULT_RETRY_MILLIS;
		}
	}
}
