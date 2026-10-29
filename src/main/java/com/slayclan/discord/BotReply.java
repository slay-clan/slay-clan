package com.slayclan.discord;

import lombok.Value;

/**
 * The bot's answer to a post. A delivered post is done, whether recorded or refused, and is never sent again;
 * one that was not delivered is kept for retry. The message, when there is one, is shown in the game chat.
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

	boolean delivered;
	String message;
	Blocker blocker;

	public static BotReply delivered(String message)
	{
		return new BotReply(true, message, null);
	}

	public static BotReply undelivered(String message)
	{
		return new BotReply(false, message, null);
	}

	public static BotReply blocked(Blocker blocker, String message)
	{
		return new BotReply(false, message, blocker);
	}

	public boolean isBlocked()
	{
		return blocker != null;
	}
}
