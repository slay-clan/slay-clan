package com.slayclan.discord;

import lombok.Value;

/**
 * A post that could not be delivered to Discord and is retried later. The screenshot is dropped because it
 * is too large to keep in the profile. The kind picks the webhook at retry time, so a rotated webhook in the clan
 * config is followed; a null kind means the points webhook.
 */
@Value
public class PendingPost
{
	String createdAt;
	WebhookPayload payload;
	PostKind kind;
}
