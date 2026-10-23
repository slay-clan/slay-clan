package com.slayclan.trigger;

import java.util.Optional;
import net.runelite.client.util.Text;

public class CollectionLogDetector
{
	private static final String NEW_ITEM_PREFIX = "New item added to your collection log: ";

	public Optional<String> detect(String chatMessage)
	{
		if (chatMessage == null)
		{
			return Optional.empty();
		}
		String text = Text.removeTags(chatMessage);
		if (!text.startsWith(NEW_ITEM_PREFIX))
		{
			return Optional.empty();
		}
		return Optional.of(text.substring(NEW_ITEM_PREFIX.length()).trim());
	}
}
