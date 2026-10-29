package com.slayclan.discord;

import lombok.ToString;
import lombok.Value;

/**
 * Where posts go and who sends them: the clan bot's API from the clan config and the member's own token from
 * the plugin settings.
 */
@Value
public class BotTarget
{
	String apiUrl;
	// Keeps the token out of every log line and assertion message that prints the target
	@ToString.Exclude
	String token;
}
