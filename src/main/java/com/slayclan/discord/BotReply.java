package com.slayclan.discord;

import lombok.Value;

/**
 * The bot's answer to a post. A delivered post is done, whether recorded or refused, and is never sent again;
 * one that was not delivered is kept for retry. The message, when there is one, is shown in the game chat. A
 * rejected token is singled out, since every post fails the same way until the member pastes a new one.
 */
@Value
public class BotReply
{
	boolean delivered;
	String message;
	boolean tokenRejected;

	public static BotReply delivered(String message)
	{
		return new BotReply(true, message, false);
	}

	public static BotReply undelivered(String message)
	{
		return new BotReply(false, message, false);
	}

	public static BotReply tokenRejected(String message)
	{
		return new BotReply(false, message, true);
	}
}
