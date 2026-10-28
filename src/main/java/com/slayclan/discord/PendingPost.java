package com.slayclan.discord;

import lombok.Value;

/**
 * A post the bot could not take and that is retried later. The screenshot is dropped because it is too large to
 * keep in the profile.
 */
@Value
public class PendingPost
{
	String createdAt;
	PostPayload payload;
}
