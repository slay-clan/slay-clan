package com.slayclan.discord;

import java.nio.charset.StandardCharsets;
import lombok.Value;

@Value
public class Attachment
{
	String filename;
	byte[] bytes;
	String mediaType;

	public static Attachment png(byte[] bytes)
	{
		return new Attachment(MessageFormatter.SCREENSHOT_FILENAME, bytes, "image/png");
	}

	public static Attachment json(String filename, String json)
	{
		return new Attachment(filename, json.getBytes(StandardCharsets.UTF_8), "application/json");
	}
}
