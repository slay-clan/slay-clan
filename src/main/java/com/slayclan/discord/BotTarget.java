package com.slayclan.discord;

import lombok.Value;

/**
 * Where posts go and who sends them: the clan bot's API from the clan config and the member's own token from
 * the plugin settings.
 */
@Value
public class BotTarget
{
	String apiUrl;
	String token;
}
