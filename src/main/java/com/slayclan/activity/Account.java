package com.slayclan.activity;

import com.slayclan.discord.PostPayload;
import lombok.Value;

/**
 * Both values are read on the client thread, so a post built later on another thread still carries them. The
 * game reports the id as -1 until it has delivered it, and a post made before that goes without one rather
 * than under an id every account shares.
 */
@Value
public class Account
{
	private static final long UNKNOWN_HASH = -1;

	String name;
	long hash;

	public PostPayload stamp(PostPayload payload)
	{
		return hash == UNKNOWN_HASH ? payload : payload.withAccountHash(String.valueOf(hash));
	}
}
