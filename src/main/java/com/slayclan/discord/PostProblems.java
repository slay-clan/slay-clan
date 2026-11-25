package com.slayclan.discord;

import com.slayclan.PluginVersion;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What went wrong while a post was made or sent, as fixed codes the bot records and shows to staff. Codes only,
 * never log text, so no file paths or other players' names leave the client.
 */
public final class PostProblems
{
	static final String FIELD = "Problems";
	public static final String VERSION_FIELD = "Plugin version";
	public static final String SCREENSHOT_FAILED = "screenshot_failed";
	public static final String SCREENSHOT_LOST = "screenshot_lost";

	private PostProblems()
	{
	}

	public static String sentOnTry(int attempt)
	{
		return "tries=" + attempt;
	}

	public static PostPayload with(PostPayload payload, String code)
	{
		Map<String, String> fields = new LinkedHashMap<>(payload.getFields());
		fields.merge(FIELD, code, (known, added) -> known + "," + added);
		fields.put(VERSION_FIELD, PluginVersion.VERSION);
		return payload.withFields(fields);
	}
}
