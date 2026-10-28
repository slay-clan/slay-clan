package com.slayclan.discord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PostKindTest
{
	@Test
	public void kindIsFoundByTheNameThePostCarries()
	{
		assertEquals(PostKind.CLOG_SNAPSHOT, PostKind.named("clog-snapshot"));
	}

	@Test
	public void onlyTheActivityFeedKindsAreQuiet()
	{
		assertTrue(PostKind.CLOG.isFeed());
		assertTrue(PostKind.STATUS.isFeed());
		assertFalse(PostKind.TASK.isFeed());
		assertFalse(PostKind.PB.isFeed());
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
