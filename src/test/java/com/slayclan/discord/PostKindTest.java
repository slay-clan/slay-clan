package com.slayclan.discord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PostKindTest
{
	@Test
	public void footerNamesTheKindAndTheNonce()
	{
		assertEquals("slayclan:clog-snapshot:0123456789abcdef", PostKind.CLOG_SNAPSHOT.footer("0123456789abcdef"));
	}

	@Test
	public void nonceIsSixteenLowercaseHexCharacters()
	{
		assertTrue(Nonce.next().matches("[0-9a-f]{16}"));
	}

	@Test
	public void noncesDiffer()
	{
		assertFalse(Nonce.next().equals(Nonce.next()));
	}
}
