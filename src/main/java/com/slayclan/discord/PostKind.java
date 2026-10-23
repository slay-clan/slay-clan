package com.slayclan.discord;

import lombok.Getter;

/**
 * Names a post in its footer so the bot can route it without reading the text: {@code slayclan:<kind>:<nonce>}.
 */
public enum PostKind
{
	TASK("task"),
	DROP("drop"),
	CLOG("clog"),
	CA("ca"),
	STATUS("status"),
	CLOG_SNAPSHOT("clog-snapshot"),
	CA_SNAPSHOT("ca-snapshot"),
	PB("pb"),
	PB_SNAPSHOT("pb-snapshot");

	private static final String PREFIX = "slayclan:";

	@Getter
	private final String name;

	PostKind(String name)
	{
		this.name = name;
	}

	public String footer(String nonce)
	{
		return PREFIX + name + ":" + nonce;
	}
}
