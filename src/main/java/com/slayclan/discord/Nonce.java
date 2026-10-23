package com.slayclan.discord;

import java.security.SecureRandom;

public final class Nonce
{
	private static final SecureRandom RANDOM = new SecureRandom();

	private Nonce()
	{
	}

	public static String next()
	{
		return String.format("%016x", RANDOM.nextLong());
	}
}
