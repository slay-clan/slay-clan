package com.slayclan.discord;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * A post the bot could not take and that is retried later. The screenshot is dropped because it is too large to
 * keep in the profile. The task it completes, if any, is kept so the bot's eventual answer can still settle it.
 */
@Value
@AllArgsConstructor
public class PendingPost
{
	String createdAt;
	PostPayload payload;
	String taskId;

	public PendingPost(String createdAt, PostPayload payload)
	{
		this(createdAt, payload, payload.getTaskId());
	}
}
