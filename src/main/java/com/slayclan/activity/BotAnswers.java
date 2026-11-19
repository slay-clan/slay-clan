package com.slayclan.activity;

import com.slayclan.discord.BotReply;

/** What the plugin does with the bot's answers: say them in the chat, and settle the task a post completed. */
public interface BotAnswers
{
	void tell(BotReply reply);

	void taskAnswered(String taskId, BotReply reply);
}
