package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

/**
 * Must only be used on the client thread. Every {@link #hold()} needs one {@link #release()}; the chat is
 * changed by the first hold and put back by the last release, so overlapping screenshots share one change.
 */
class ScreenshotChat
{
	static final int ALL_TAB = 0;
	static final int GAME_TAB = 1;
	// chat_view while the resizable chatbox is closed, set by the game's [proc,chat_button_switch] (script 2823)
	static final int CLOSED_CHATBOX = 1337;
	// The game's [clientscript,chat_button_onop](op, tab) behind the chat tab buttons, op 1 being a left click
	static final int CHAT_TAB_BUTTON_SCRIPT = 175;
	private static final int LEFT_CLICK = 1;
	private static final int NO_TAB = -1;
	private static final int PRIVATE_CHAT_IN_CHATBOX = 0;
	private static final String CHAT_LINE_CHECK = "chatFilterCheck";
	// Group ironman chat reaches the chat builder with the clan chat type, its own type is only given out in RuneLite events
	private static final Set<ChatMessageType> PLAYER_CHAT = EnumSet.of(
		ChatMessageType.PUBLICCHAT,
		ChatMessageType.MODCHAT,
		ChatMessageType.AUTOTYPER,
		ChatMessageType.MODAUTOTYPER,
		ChatMessageType.PRIVATECHAT,
		ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.MODPRIVATECHAT,
		ChatMessageType.FRIENDSCHAT,
		ChatMessageType.CLAN_CHAT,
		ChatMessageType.CLAN_GUEST_CHAT
	);

	private final Client client;
	private final SlayClanConfig config;
	private int holds;
	private boolean playerChatHidden;
	private boolean privateMessagesHidden;
	private int openedTab = NO_TAB;
	private int chatViewBefore;

	ScreenshotChat(Client client, SlayClanConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void hold()
	{
		holds++;
		if (!isFirstHoldInGame())
		{
			return;
		}
		playerChatHidden = config.hidePlayerChat();
		boolean tabClicked = openGameChat();
		if (needsOwnRebuild(playerChatHidden, tabClicked))
		{
			rebuildChat();
		}
		privateMessagesHidden = hidePrivateMessages();
	}

	void release()
	{
		if (--holds > 0)
		{
			return;
		}
		if (!isLoggedIn())
		{
			forget();
			return;
		}
		boolean rebuildAfterFrame = chatNeedsRebuildAfterFrame();
		if (privateMessagesToShowAgain())
		{
			showPrivateMessages();
		}
		playerChatHidden = false;
		privateMessagesHidden = false;
		boolean tabClicked = closeGameChat();
		if (needsOwnRebuild(rebuildAfterFrame, tabClicked))
		{
			rebuildChat();
		}
	}

	boolean changesTheChat()
	{
		return playerChatHidden || privateMessagesHidden || openedTab != NO_TAB;
	}

	void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!isLineCheckWhileHiding(event))
		{
			return;
		}
		// RuneLite's chat builder scripts push the keep flag, the message type and the message id, in that order
		int[] intStack = client.getIntStack();
		int size = client.getIntStackSize();
		if (isPlayerChat(intStack[size - 2]))
		{
			intStack[size - 3] = 0;
		}
	}

	private boolean isFirstHoldInGame()
	{
		return holds == 1 && isLoggedIn();
	}

	private boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	private void forget()
	{
		playerChatHidden = false;
		privateMessagesHidden = false;
		openedTab = NO_TAB;
	}

	private boolean isLineCheckWhileHiding(ScriptCallbackEvent event)
	{
		return playerChatHidden && CHAT_LINE_CHECK.equals(event.getEventName());
	}

	private static boolean isPlayerChat(int messageType)
	{
		return PLAYER_CHAT.contains(ChatMessageType.of(messageType));
	}

	// A tab click already rebuilds the chatbox and the split private chat
	private static boolean needsOwnRebuild(boolean rebuildWanted, boolean tabClicked)
	{
		return rebuildWanted && !tabClicked;
	}

	// The game rebuilds the split private chat as well, so its own idea of whether that box shows wins
	private boolean chatNeedsRebuildAfterFrame()
	{
		return playerChatHidden || privateMessagesHidden;
	}

	private boolean openGameChat()
	{
		if (!config.showGameChat())
		{
			return false;
		}
		int chatView = client.getVarcIntValue(VarClientID.CHAT_VIEW);
		if (showsGameMessages(chatView))
		{
			return false;
		}
		// With player chat hidden, All adds the clan's drop broadcasts to the game messages without any chat
		openedTab = playerChatHidden ? ALL_TAB : GAME_TAB;
		chatViewBefore = chatView;
		clickChatTab(openedTab);
		return true;
	}

	private static boolean showsGameMessages(int chatView)
	{
		return chatView == ALL_TAB || chatView == GAME_TAB;
	}

	private boolean closeGameChat()
	{
		if (openedTab == NO_TAB)
		{
			return false;
		}
		int tab = openedTab;
		openedTab = NO_TAB;
		if (client.getVarcIntValue(VarClientID.CHAT_VIEW) != tab)
		{
			return false;
		}
		// Clicking the open tab again is how the game closes the chatbox
		clickChatTab(chatViewBefore == CLOSED_CHATBOX ? tab : chatViewBefore);
		return true;
	}

	private void clickChatTab(int tab)
	{
		client.runScript(CHAT_TAB_BUTTON_SCRIPT, LEFT_CLICK, tab);
	}

	private void rebuildChat()
	{
		client.runScript(ScriptID.SPLITPM_CHANGED);
	}

	private boolean hidePrivateMessages()
	{
		Widget privateMessages = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (nothingToHide(privateMessages))
		{
			return false;
		}
		privateMessages.setHidden(true);
		return true;
	}

	private boolean nothingToHide(Widget privateMessages)
	{
		return !config.hidePrivateMessages() || !isShowing(privateMessages);
	}

	private static boolean isShowing(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private boolean privateMessagesToShowAgain()
	{
		return privateMessagesHidden && client.getVarpValue(VarPlayerID.OPTION_PM) != PRIVATE_CHAT_IN_CHATBOX;
	}

	// Looked up again rather than kept, since the game may have rebuilt the interface while the frame was drawn
	private void showPrivateMessages()
	{
		Widget privateMessages = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (privateMessages != null)
		{
			privateMessages.setHidden(false);
		}
	}
}
