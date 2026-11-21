package com.slayclan.discord;

import java.util.function.Consumer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptCallbackEvent;
import static org.mockito.Mockito.lenient;

final class ChatLineCheck
{
	private static final int KEEP = 1;
	private static final int MESSAGE_ID = 7;

	private ChatLineCheck()
	{
	}

	static boolean shownInChat(Client client, Consumer<ScriptCallbackEvent> chatBuilderCallback, ChatMessageType type)
	{
		int[] intStack = {KEEP, type.getType(), MESSAGE_ID};
		lenient().when(client.getIntStack()).thenReturn(intStack);
		lenient().when(client.getIntStackSize()).thenReturn(intStack.length);
		ScriptCallbackEvent event = new ScriptCallbackEvent();
		event.setEventName("chatFilterCheck");
		chatBuilderCallback.accept(event);
		return intStack[0] == KEEP;
	}
}
