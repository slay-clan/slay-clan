package com.slayclan.discord;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.slayclan.config.SecretSafeUrl;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends posts to the clan bot with the member's token. The bot answers every post at once: recorded, awaiting
 * the member's answer in Discord, a duplicate, or refused with a reason. All of those are delivered. A post the
 * bot could not take (unreachable, server error, rate limited, token not accepted) is reported as undelivered
 * so the caller can keep it. The token is never logged.
 */
@Slf4j
@Singleton
public class BotClient
{
	static final String POST_PART = "post";
	static final String SCREENSHOT_PART = "screenshot";
	private static final String STATUS_DUPLICATE = "duplicate";
	private static final String STATUS_RECORDED = "recorded";
	private static final long DEFAULT_RETRY_MILLIS = 1_000;
	// The bot's limit resets within a minute; anything longer is left to the pending post retry
	private static final long MAX_RETRY_MILLIS = 60_000;
	private static final long RESPONSE_TIMEOUT_SECONDS = 60;
	private static final int UNAUTHORIZED = 401;
	private static final int NOT_FOUND = 404;
	private static final int TOO_LARGE = 413;
	private static final int TOO_MANY = 429;
	private static final String SCREENSHOT_TOO_LARGE = "Not sent: the screenshot was too large for the clan bot. Submit it by hand in Discord.";
	private static final String WRONG_ADDRESS = "The Clan bot URL in the plugin settings does not lead to the clan bot. Check it against the one in the clan Discord. Your posts are kept until it works.";
	private static final String UNREACHABLE = "The clan bot could not be reached. Check the Clan bot URL in the plugin settings; if it is right, the bot may be down. Your posts are kept.";
	private static final String TOKEN_REJECTED = "The clan bot did not accept your clan token. Run /plugin token in the clan Discord and paste the new token into the plugin settings.";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Map<ScheduledFuture<?>, CompletableFuture<BotReply>> scheduledRetries = new ConcurrentHashMap<>();

	@Value
	private static class Answer
	{
		String status;
		String message;
	}

	@Inject
	public BotClient(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor)
	{
		// The bot writes the sheet and posts the review message before it answers, and handles posts one at a
		// time, so a login burst takes well over OkHttp's ten second default. Redirects are not followed, since
		// a post may only go to the address the member set, never to one a response names
		this.httpClient = httpClient.newBuilder()
			.readTimeout(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
		this.gson = gson;
		this.executor = executor;
	}

	public CompletableFuture<BotReply> send(BotTarget target, PostPayload payload, byte[] png)
	{
		CompletableFuture<BotReply> result = new CompletableFuture<>();
		post(target, payload, png, true, result);
		return result;
	}

	/**
	 * Drops rate limit retries that have not run yet. Their futures complete as undelivered, so the posts are kept.
	 */
	public void cancelRetries()
	{
		for (Map.Entry<ScheduledFuture<?>, CompletableFuture<BotReply>> retry : scheduledRetries.entrySet())
		{
			if (retry.getKey().cancel(false))
			{
				retry.getValue().complete(BotReply.undelivered(null));
			}
		}
		scheduledRetries.clear();
	}

	int pendingRetryCount()
	{
		return scheduledRetries.size();
	}

	private RequestBody buildBody(PostPayload payload, byte[] png)
	{
		MultipartBody.Builder builder = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart(POST_PART, gson.toJson(payload));
		if (png != null)
		{
			builder.addFormDataPart(SCREENSHOT_PART, MessageFormatter.SCREENSHOT_FILENAME,
				RequestBody.create(MediaType.parse("image/png"), png));
		}
		return builder.build();
	}

	private void post(BotTarget target, PostPayload payload, byte[] png, boolean retryOnRateLimit, CompletableFuture<BotReply> result)
	{
		// Checked here, where the data leaves, as well as before posting: the setting can change in between
		if (!SecretSafeUrl.accepts(target.getBotUrl()))
		{
			result.complete(BotReply.blocked(BotReply.Blocker.WRONG_ADDRESS, WRONG_ADDRESS));
			return;
		}
		Request request = new Request.Builder()
			.url(target.getBotUrl())
			.header("Authorization", "Bearer " + target.getToken())
			.post(buildBody(payload, png))
			.build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Could not reach the clan bot", e);
				result.complete(noAnswer(e));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (rateLimitedWithRetryLeft(response, retryOnRateLimit))
					{
						scheduleRetry(target, payload, png, result, retryAfterMillis(response));
						return;
					}
					result.complete(replyTo(payload, response));
				}
			}
		});
	}

	private BotReply replyTo(PostPayload payload, Response response)
	{
		Answer answer = answerOf(response);
		if (response.isSuccessful())
		{
			return BotReply.delivered(chatLine(payload, answer));
		}
		if (response.code() == UNAUTHORIZED)
		{
			return BotReply.blocked(BotReply.Blocker.TOKEN_REJECTED, TOKEN_REJECTED);
		}
		if (leadsElsewhere(response))
		{
			log.warn("The clan bot URL answered {}, it does not lead to the bot", response.code());
			return BotReply.blocked(BotReply.Blocker.WRONG_ADDRESS, WRONG_ADDRESS);
		}
		// A post without its screenshot would be unreviewable, so it is dropped with a clear word instead of resent
		if (response.code() == TOO_LARGE)
		{
			return BotReply.delivered(SCREENSHOT_TOO_LARGE);
		}
		if (botCouldNotTakeIt(response))
		{
			log.warn("The clan bot answered {} to a {} post", response.code(), payload.getKind());
			return BotReply.undelivered(null);
		}
		log.warn("The clan bot rejected a {} post with status {}", payload.getKind(), response.code());
		return BotReply.delivered(answer == null ? "The clan bot rejected a post." : answer.getMessage());
	}

	private static boolean rateLimitedWithRetryLeft(Response response, boolean retryOnRateLimit)
	{
		return response.code() == TOO_MANY && retryOnRateLimit;
	}

	private static boolean botCouldNotTakeIt(Response response)
	{
		return response.code() >= 500 || response.code() == TOO_MANY;
	}

	// A redirect or a missing page means the member's Clan bot URL is off; the post itself is fine and waits
	private static boolean leadsElsewhere(Response response)
	{
		return response.isRedirect() || response.code() == NOT_FOUND;
	}

	// A host that does not resolve or refuses the connection is a wrong address or a bot that is down; a slow
	// answer is neither and is simply retried later
	private static BotReply noAnswer(IOException e)
	{
		if (e instanceof UnknownHostException || e instanceof ConnectException)
		{
			return BotReply.blocked(BotReply.Blocker.UNREACHABLE, UNREACHABLE);
		}
		return BotReply.undelivered(null);
	}

	// Bookkeeping posts are sent quietly; only what the member did right now, or anything that went wrong, is said in chat
	private static String chatLine(PostPayload payload, Answer answer)
	{
		return nothingToSay(answer) || quietReceipt(payload, answer) ? null : answer.getMessage();
	}

	private static boolean nothingToSay(Answer answer)
	{
		return answer == null || STATUS_DUPLICATE.equals(answer.getStatus());
	}

	private static boolean quietReceipt(PostPayload payload, Answer answer)
	{
		return STATUS_RECORDED.equals(answer.getStatus()) && payload.isReceiptQuiet();
	}

	private Answer answerOf(Response response)
	{
		try
		{
			return response.body() == null ? null : gson.fromJson(response.body().string(), Answer.class);
		}
		catch (IOException | JsonParseException e)
		{
			return null;
		}
	}

	private void scheduleRetry(BotTarget target, PostPayload payload, byte[] png, CompletableFuture<BotReply> result, long delayMillis)
	{
		ScheduledFuture<?> retry;
		try
		{
			retry = executor.schedule(() -> post(target, payload, png, false, result), delayMillis, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// The client is shutting down; the post is kept rather than lost
			result.complete(BotReply.undelivered(null));
			return;
		}
		scheduledRetries.put(retry, result);
		result.whenComplete((reply, error) -> scheduledRetries.remove(retry));
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
