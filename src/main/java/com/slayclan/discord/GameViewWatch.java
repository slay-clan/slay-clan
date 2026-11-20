package com.slayclan.discord;

import java.util.function.BooleanSupplier;

/**
 * Tells when the game world has been on screen for a moment after login, so a post judged at login is captured
 * with the player and the chat in view rather than the welcome screen.
 */
public class GameViewWatch
{
	// Lets the scene and the chatbox draw once the welcome screen is gone
	static final int SETTLE_TICKS = 2;
	// A member who leaves the welcome screen open still gets the post, with the welcome screen in the frame
	static final int WELCOME_SCREEN_TIMEOUT_TICKS = 100;

	private final BooleanSupplier welcomeScreenOpen;
	private int loginTick;
	private int shownSince = -1;

	public GameViewWatch(BooleanSupplier welcomeScreenOpen)
	{
		this.welcomeScreenOpen = welcomeScreenOpen;
	}

	public void onLogin(int tick)
	{
		loginTick = tick;
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
		return settled(tick) || leftOpenTooLong(tick);
	}

	private boolean leftOpenTooLong(int tick)
	{
		return shownSince == -1 && tick - loginTick >= WELCOME_SCREEN_TIMEOUT_TICKS;
	}

	private boolean settled(int tick)
	{
		return shownSince != -1 && tick - shownSince >= SETTLE_TICKS;
	}
}
