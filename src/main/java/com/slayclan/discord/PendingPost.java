package com.slayclan.discord;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * A post the bot could not take and that is retried later. Its screenshot is too large to keep in the profile,
 * so it waits on disk and the post only remembers that there is one. The task it completes, if any, is kept so
 * the bot's eventual answer can still settle it.
 */
@Value
@AllArgsConstructor
public class PendingPost
{
	String createdAt;
	PostPayload payload;
	String taskId;
	boolean screenshotKept;
	// Posts kept before sends were counted read back as 0
	@With
	int failedSends;

	public PendingPost(String createdAt, PostPayload payload)
	{
		this(createdAt, payload, false);
	}

	public PendingPost(String createdAt, PostPayload payload, boolean screenshotKept)
	{
		this(createdAt, payload, payload.getTaskId(), screenshotKept, 1);
	}

	/** The number of the next send, counting the first one that failed. */
	public int nextTry()
	{
		return Math.max(1, failedSends) + 1;
	}
}
