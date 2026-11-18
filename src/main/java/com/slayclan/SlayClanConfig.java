package com.slayclan;

import com.slayclan.discord.ChatInScreenshot;
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
	String CHAT_IN_SCREENSHOT_KEY = "chatInScreenshot";

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
		keyName = CHAT_IN_SCREENSHOT_KEY,
		name = "Chat in screenshots",
		description = "What the chat shows in posted screenshots. Hiding private messages needs \"Split friends private chat\" on in the game settings, otherwise they sit in the chatbox. The chat flickers once per screenshot",
		position = 4
	)
	default ChatInScreenshot chatInScreenshot()
	{
		return ChatInScreenshot.HIDE_PRIVATE_MESSAGES;
	}
}
