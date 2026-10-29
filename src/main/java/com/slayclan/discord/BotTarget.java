package com.slayclan.discord;

import lombok.ToString;
import lombok.Value;

/**
 * Where posts go and who sends them, both from the member's plugin settings.
 */
@Value
public class BotTarget
{
	String botUrl;
	// Keeps the token out of every log line and assertion message that prints the target
	@ToString.Exclude
	String token;
}
