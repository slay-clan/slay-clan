package com.slayclan.discord;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * A post as the clan bot reads it: the kind, a nonce that makes a retry safe, the account it was made on and
 * the fields of that kind. Snapshots carry their entries inline. Field names are the bot's contract. A quiet
 * post is bookkeeping the member did not cause right now, so its receipt stays out of the chat; the flag is
 * not sent and not kept for a retry.
 */
@Value
public class PostPayload
{
	String kind;
	String nonce;
	String account;
	String accountHash;
	String at;
	Map<String, String> fields;
	List<?> entries;
	transient boolean quiet;

	public boolean isReceiptQuiet()
	{
		return quiet || postKind().isFeed();
	}

	public PostKind postKind()
	{
		return PostKind.named(kind);
	}
}
