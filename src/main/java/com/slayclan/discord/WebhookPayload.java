package com.slayclan.discord;

import java.util.List;
import lombok.Value;

@Value
public class WebhookPayload
{
	List<Embed> embeds;

	@Value
	public static class Embed
	{
		String title;
		String description;
		int color;
		String timestamp;
		EmbedImage image;
		List<Field> fields;
		Footer footer;
	}

	@Value
	public static class EmbedImage
	{
		String url;
	}

	@Value
	public static class Footer
	{
		String text;
	}

	@Value
	public static class Field
	{
		String name;
		String value;
		boolean inline;
	}
}
