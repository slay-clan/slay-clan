package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScreenshotChatTest
{
	@Mock
	private Client client;
	@Mock
	private SlayClanConfig config;

	private ScreenshotChat chat;

	@Before
	public void setUp()
	{
		lenient().when(config.hidePlayerChat()).thenReturn(true);
		lenient().when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		chat = new ScreenshotChat(client, config);
	}

	@Test
	public void everyKindOfPlayerChatIsLeftOutOfTheScreenshot()
	{
		chat.hold();

		for (ChatMessageType type : new ChatMessageType[]{
			ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT, ChatMessageType.AUTOTYPER, ChatMessageType.MODAUTOTYPER,
			ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT, ChatMessageType.MODPRIVATECHAT,
			ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT})
		{
			assertFalse(type.name(), shownInChat(type));
		}
	}

	@Test
	public void gameMessagesAndClanBroadcastsStayInTheScreenshot()
	{
		chat.hold();

		for (ChatMessageType type : new ChatMessageType[]{
			ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM, ChatMessageType.ENGINE, ChatMessageType.BROADCAST,
			ChatMessageType.CLAN_MESSAGE, ChatMessageType.CLAN_GUEST_MESSAGE, ChatMessageType.FRIENDSCHATNOTIFICATION})
		{
			assertTrue(type.name(), shownInChat(type));
		}
	}

	@Test
	public void playerChatIsShownAgainOnceTheScreenshotIsTaken()
	{
		chat.hold();
		chat.release();

		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void playerChatStaysInTheScreenshotWhenTheSettingIsOff()
	{
		when(config.hidePlayerChat()).thenReturn(false);

		chat.hold();

		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void playerChatOutsideAScreenshotIsLeftAlone()
	{
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
	}

	private boolean shownInChat(ChatMessageType type)
	{
		return ChatLineCheck.shownInChat(client, chat::onScriptCallbackEvent, type);
	}
}
