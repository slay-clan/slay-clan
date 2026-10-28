package com.slayclan.discord;

import lombok.Value;

/**
 * The bot's answer to a post. A delivered post is done, whether recorded or refused, and is never sent again;
 * one that was not delivered is kept for retry. The message, when there is one, is shown in the game chat.
 */
@Value
public class BotReply
{
	boolean delivered;
	String message;

	public static BotReply delivered(String message)
	{
		return new BotReply(true, message);
	}

	public static BotReply undelivered(String message)
	{
		return new BotReply(false, message);
	}
}
