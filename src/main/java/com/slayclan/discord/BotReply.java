package com.slayclan.discord;

import lombok.Value;

/**
 * The bot's answer to a post. A delivered post is answered, recorded or refused, and is never sent again as
 * it is; one that was not delivered is kept for retry. A refusal carries the bot's reason, so the caller can
 * tell one that settles the matter from one the member can still put right. The message, when there is one,
 * is shown in the game chat.
 */
@Value
public class BotReply
{
	/**
	 * Why no post can get through until the member fixes a setting or the bot comes back. Every post fails the
	 * same way meanwhile, so a retry round stops at the first one and the chat says it once.
	 */
	public enum Blocker
	{
		TOKEN_REJECTED,
		WRONG_ADDRESS,
		UNREACHABLE
	}

	public enum Verdict
	{
		RECORDED,
		REFUSED,
		UNANSWERED
	}

	boolean delivered;
	String message;
	Blocker blocker;
	Verdict verdict;
	String reason;
	boolean notSent;

	public static BotReply recorded(String message)
	{
		return new BotReply(true, message, null, Verdict.RECORDED, null, false);
	}

	/** Recorded like any other answer, but the bot took it no further and said why. */
	public static BotReply notSent(String message)
	{
		return new BotReply(true, message, null, Verdict.RECORDED, null, true);
	}

	public static BotReply refused(String message, String reason)
	{
		return new BotReply(true, message, null, Verdict.REFUSED, reason, false);
	}

	public static BotReply undelivered(String message)
	{
		return new BotReply(false, message, null, Verdict.UNANSWERED, null, false);
	}

	public static BotReply blocked(Blocker blocker, String message)
	{
		return new BotReply(false, message, blocker, Verdict.UNANSWERED, null, false);
	}

	public boolean isBlocked()
	{
		return blocker != null;
	}

	public boolean isRecorded()
	{
		return verdict == Verdict.RECORDED;
	}

	public boolean isRefused()
	{
		return verdict == Verdict.REFUSED;
	}

	public boolean isPbNotPosted(PostKind kind)
	{
		return kind == PostKind.PB && notSent;
	}
}
