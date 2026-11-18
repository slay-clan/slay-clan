package com.slayclan.activity;

import com.slayclan.discord.PostPayload;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AccountTest
{
	private final PostPayload post = new PostPayload("task", "0123456789abcdef", "Clanmate", null, "2026-11-17T12:00:00Z",
		Map.of("Task", "Any Kill"), null, true);

	@Test
	public void aPostCarriesTheGamesIdForTheAccountItWasMadeOn()
	{
		assertEquals("42", new Account("Clanmate", 42L).stamp(post).getAccountHash());
	}

	@Test
	public void aPostMadeBeforeTheGameHasDeliveredTheAccountIdCarriesNoId()
	{
		assertNull(new Account("Clanmate", -1L).stamp(post).getAccountHash());
	}

	@Test
	public void aPostThatKeepsItsReceiptOutOfTheChatStillDoesSoAfterTheAccountIsStamped()
	{
		assertTrue(new Account("Clanmate", 42L).stamp(post).isReceiptQuiet());
	}
}
