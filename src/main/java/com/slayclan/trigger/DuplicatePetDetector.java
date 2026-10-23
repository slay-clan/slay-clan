package com.slayclan.trigger;

import java.util.Optional;
import net.runelite.client.util.Text;

public class DuplicatePetDetector
{
	private static final String DUPLICATE_PET_PREFIX = "You have a funny feeling like you would have been followed";

	public Optional<String> detect(String chatMessage)
	{
		if (chatMessage == null || !Text.removeTags(chatMessage).startsWith(DUPLICATE_PET_PREFIX))
		{
			return Optional.empty();
		}
		return Optional.of(Triggers.PET_DUPLICATE);
	}
}
