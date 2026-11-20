package com.slayclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayClanConfig.GROUP)
public interface SlayClanConfig extends Config
{
	String GROUP = "slayclan";
	// Task memory is kept outside GROUP so that resetting the plugin settings does not repost completed tasks
	String MEMORY_GROUP = "slayclanmemory";
	String ENABLED_KEY = "enabled";
	String CONFIG_URL_KEY = "configUrl";
	String CLAN_TOKEN_KEY = "clanToken";
	String BOT_URL_KEY = "botUrl";
	String HIDE_PRIVATE_MESSAGES_KEY = "hidePrivateMessages";
	String SHOW_GAME_CHAT_KEY = "showGameChat";
	String HIDE_PLAYER_CHAT_KEY = "hidePlayerChat";

	@ConfigItem(
		keyName = ENABLED_KEY,
		name = "Enable posting",
		description = "Fetch your clan's config and post completed clan tasks, your collection log and combat achievement progress and your personal bests to its Discord",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean enabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = CONFIG_URL_KEY,
		name = "Clan config URL",
		description = "URL to the JSON config provided by your clan",
		position = 1
	)
	default String configUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = BOT_URL_KEY,
		name = "Clan bot URL",
		description = "Where your clan's bot receives posts, provided by your clan. Must be https",
		position = 2
	)
	default String botUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = CLAN_TOKEN_KEY,
		name = "Clan token",
		description = "Your personal token from the clan Discord: run /plugin token there and paste the answer here. It tells the clan's bot that the posts are yours",
		secret = true,
		position = 3
	)
	default String clanToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = SHOW_GAME_CHAT_KEY,
		name = "Show game chat in screenshots",
		description = "Opens the chatbox on the All tab (or the Game tab while player chat is shown) for the moment of a screenshot when it is closed or on another tab, so staff can see the kill count, time or drop line. Your tab and closed chatbox come back right after",
		position = 4
	)
	default boolean showGameChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDE_PLAYER_CHAT_KEY,
		name = "Hide player chat in screenshots",
		description = "Leaves every message written by a player out of posted screenshots: public, private, friends chat, clan, guest clan and group chat, including your own. Game messages and clan broadcasts stay. The chat flickers once per screenshot",
		position = 5
	)
	default boolean hidePlayerChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDE_PRIVATE_MESSAGES_KEY,
		name = "Hide private messages",
		description = "Leaves the split private chat above the chatbox out of posted screenshots, with your friends' login notices. Only matters with \"Split friends private chat\" on in the game settings, private messages inside the chatbox are left out by Hide player chat in screenshots. The chat flickers once per screenshot",
		position = 6
	)
	default boolean hidePrivateMessages()
	{
		return true;
	}
}
