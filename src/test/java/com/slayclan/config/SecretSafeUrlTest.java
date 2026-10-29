package com.slayclan.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SecretSafeUrlTest
{
	@Test
	public void acceptsAnHttpsAddress()
	{
		assertTrue(SecretSafeUrl.accepts("https://bot.example/api/v1/posts"));
	}

	@Test
	public void acceptsPlainHttpOnThisComputerForTesting()
	{
		assertTrue(SecretSafeUrl.accepts("http://127.0.0.1:3000/api/v1/posts"));
		assertTrue(SecretSafeUrl.accepts("http://localhost:3000/api/v1/posts"));
	}

	@Test
	public void rejectsPlainHttpToAnotherComputer()
	{
		assertFalse(SecretSafeUrl.accepts("http://bot.example/api/v1/posts"));
	}

	@Test
	public void rejectsAnAddressThatOnlyLooksLocalInFrontOfTheUserinfoSeparator()
	{
		assertFalse(SecretSafeUrl.accepts("http://localhost:@evil.example/api/v1/posts"));
	}

	@Test
	public void rejectsAnEmptyOrBrokenAddress()
	{
		assertFalse(SecretSafeUrl.accepts(""));
		assertFalse(SecretSafeUrl.accepts((String) null));
		assertFalse(SecretSafeUrl.accepts("https://"));
	}
}
