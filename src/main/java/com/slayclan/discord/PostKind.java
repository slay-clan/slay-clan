package com.slayclan.discord;

import lombok.Getter;

/**
 * Names a post so the bot can route it without reading the fields.
 */
public enum PostKind
{
	TASK("task", false),
	DROP("drop", false),
	CLOG("clog", true),
	CA("ca", true),
	STATUS("status", true),
	CLOG_SNAPSHOT("clog-snapshot", true),
	CA_SNAPSHOT("ca-snapshot", true),
	PB("pb", false),
	PB_SNAPSHOT("pb-snapshot", true);

	@Getter
	private final String name;
	// The activity feed is sent without the member doing anything, so its receipts stay out of the chat
	@Getter
	private final boolean feed;

	PostKind(String name, boolean feed)
	{
		this.name = name;
		this.feed = feed;
	}

	public static PostKind named(String name)
	{
		for (PostKind kind : values())
		{
			if (kind.name.equals(name))
			{
				return kind;
			}
		}
		throw new IllegalArgumentException("Unknown post kind " + name);
	}
}
