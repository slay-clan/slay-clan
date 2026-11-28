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
	String SHOW_PB_NOT_SENT_KEY = "showPbNotSent";

	@ConfigItem(
		keyName = ENABLED_KEY,
		name = "Enable posting",
		description = "Post your completed clan tasks, drops and personal bests to your clan's Discord for staff to approve",
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
		description = "Opens the chat on the All tab in screenshots so staff can see the kill count, time and drop lines they approve from. The chat flickers once when it is switched. On by default",
		position = 4
	)
	default boolean showGameChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDE_PLAYER_CHAT_KEY,
		name = "Hide player chat in screenshots",
		description = "Hides messages from players in screenshots so your and others' chat stays private. Game messages stay. The chat flickers once. On by default",
		position = 5
	)
	default boolean hidePlayerChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDE_PRIVATE_MESSAGES_KEY,
		name = "Hide private messages",
		description = "Hides the split private chat above the chatbox in screenshots so private messages are not posted. On by default",
		position = 6
	)
	default boolean hidePrivateMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = SHOW_PB_NOT_SENT_KEY,
		name = "Show PBs the bot did not post",
		description = "Shows a chat line when the clan bot does not post a personal best, for example when it is under the board minimum or not faster than your season best",
		position = 7
	)
	default boolean showPbNotSent()
	{
		return false;
	}
}
