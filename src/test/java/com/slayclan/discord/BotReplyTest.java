package com.slayclan.discord;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BotReplyTest
{
	@Test
	public void aPbTheBotRecordedButDidNotSendToTheBoardCountsAsNotPosted()
	{
		BotReply reply = BotReply.notSent("Personal best 1:00.00 at Zulrah not sent to the board: under the minimum.");

		assertTrue(reply.isPbNotPosted(PostKind.PB));
	}

	@Test
	public void aNotSentAnswerStaysDeliveredAndRecorded()
	{
		BotReply reply = BotReply.notSent("Not sent to the board.");

		assertTrue(reply.isDelivered());
		assertTrue(reply.isRecorded());
	}

	@Test
	public void aClanTaskNotSentForReviewIsNotAPbTheBotDidNotPost()
	{
		BotReply reply = BotReply.notSent("Clan task Any raid (Easy) not sent for review: you cannot get points for this task right now.");

		assertFalse(reply.isPbNotPosted(PostKind.TASK));
	}

	@Test
	public void aRefusedPbIsNeverHiddenBecauseTheMemberMustActOnIt()
	{
		BotReply reply = BotReply.refused("Not sent: the screenshot is missing.", "the screenshot is missing.");

		assertFalse(reply.isPbNotPosted(PostKind.PB));
	}

	@Test
	public void aRecordedPbWithoutANoteIsNotHidden()
	{
		assertFalse(BotReply.recorded("Recorded.").isPbNotPosted(PostKind.PB));
	}
}
