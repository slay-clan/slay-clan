package com.slayclan.task;

import com.slayclan.discord.BotReply;
import java.util.Set;

/**
 * Whether a refused task post leaves the task done. The bot refuses a one-time task the member already
 * holds, approved or still in review, and that is final. Any other refusal, a name not listed in the clan
 * sheet, a rank too low, a task missing from the sheet, is about the member's setup, so the task must be
 * free to post again once that is fixed.
 */
public final class TaskRefusal
{
	// The bot's own reason texts for a task the member already has (PluginTaskSubmission in the bot)
	private static final Set<String> ALREADY_HELD = Set.of(
		"you already have it approved.",
		"it is already awaiting review.");

	private TaskRefusal()
	{
	}

	public static boolean closesTheTask(BotReply reply)
	{
		return reply.isRefused() && reply.getReason() != null && ALREADY_HELD.contains(reply.getReason().trim());
	}
}
