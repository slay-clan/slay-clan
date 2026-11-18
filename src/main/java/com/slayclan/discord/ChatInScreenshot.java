package com.slayclan.discord;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatInScreenshot
{
	SHOW_ALL("Show all"),
	HIDE_PRIVATE_MESSAGES("Hide private messages"),
	HIDE_CHATBOX("Hide chatbox");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
