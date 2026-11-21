package com.slayclan.discord;

import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.With;

/**
 * A post as the clan bot reads it: the kind, a nonce that makes a retry safe, the account it was made on and
 * the fields of that kind. Snapshots carry their entries inline. Field names are the bot's contract. A feed
 * post is bookkeeping the member did not cause right now, so its receipt stays out of the chat. The task the
 * post completes, if any, is not sent; it lets the bot's answer settle the task.
 */
@Value
@With
public class PostPayload
{
	String kind;
	String nonce;
	String account;
	String accountHash;
	String at;
	Map<String, String> fields;
	List<?> entries;
	transient String taskId;

	public boolean isReceiptQuiet()
	{
		return postKind().isFeed();
	}

	public PostKind postKind()
	{
		return PostKind.named(kind);
	}
}
