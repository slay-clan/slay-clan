package com.slayclan.discord;

import java.util.function.BooleanSupplier;

/**
 * Tells when the game world has been on screen for a moment after login, so a post judged at login is captured
 * with the player and the chat in view rather than the welcome screen. A member who stays on the welcome screen
 * keeps the post waiting; one who logs out from there is judged again at the next login.
 */
public class GameViewWatch
{
	// Lets the scene and the chatbox draw once the welcome screen is gone
	static final int SETTLE_TICKS = 2;

	private final BooleanSupplier welcomeScreenOpen;
	private int shownSince = -1;

	public GameViewWatch(BooleanSupplier welcomeScreenOpen)
	{
		this.welcomeScreenOpen = welcomeScreenOpen;
	}

	public void onLogin()
	{
		shownSince = -1;
	}

	public void onTick(int tick)
	{
		if (welcomeScreenOpen.getAsBoolean())
		{
			shownSince = -1;
		}
		else if (shownSince == -1)
		{
			shownSince = tick;
		}
	}

	public boolean shown(int tick)
	{
		return shownSince != -1 && tick - shownSince >= SETTLE_TICKS;
	}
}
