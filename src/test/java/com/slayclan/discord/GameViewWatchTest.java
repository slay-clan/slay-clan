package com.slayclan.discord;

import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GameViewWatchTest
{
	private final AtomicBoolean welcomeScreenOpen = new AtomicBoolean(true);
	private final GameViewWatch watch = new GameViewWatch(welcomeScreenOpen::get);

	@Test
	public void whileTheWelcomeScreenIsOpenThePlayerIsNotYetInView()
	{
		watch.onLogin(0);
		for (int tick = 0; tick < 20; tick++)
		{
			watch.onTick(tick);
		}

		assertFalse(watch.shown(19));
	}

	@Test
	public void thePlayerIsInViewAMomentAfterTheWelcomeScreenCloses()
	{
		watch.onLogin(0);
		watch.onTick(0);
		welcomeScreenOpen.set(false);
		watch.onTick(10);
		watch.onTick(11);

		assertFalse(watch.shown(11));
		watch.onTick(12);
		assertTrue(watch.shown(12));
	}

	@Test
	public void aWelcomeScreenLeftOpenNoLongerHoldsThePostAfterAMinute()
	{
		watch.onLogin(0);
		watch.onTick(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS);

		assertTrue(watch.shown(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS));
	}

	@Test
	public void aNewLoginWaitsForItsOwnWelcomeScreenToClose()
	{
		watch.onLogin(0);
		welcomeScreenOpen.set(false);
		watch.onTick(0);
		watch.onTick(5);
		assertTrue(watch.shown(5));

		watch.onLogin(50);
		welcomeScreenOpen.set(true);
		watch.onTick(51);

		assertFalse(watch.shown(55));
	}

	@Test
	public void aWelcomeScreenThatOpensAFewTicksAfterLoginStillHoldsThePost()
	{
		watch.onLogin(0);
		welcomeScreenOpen.set(false);
		watch.onTick(0);
		watch.onTick(1);
		welcomeScreenOpen.set(true);
		watch.onTick(2);
		watch.onTick(5);

		assertFalse(watch.shown(5));
	}

	@Test
	public void aWelcomeScreenClosedJustBeforeTheMinuteStillGetsAMomentToDrawTheGame()
	{
		watch.onLogin(0);
		watch.onTick(0);
		welcomeScreenOpen.set(false);
		watch.onTick(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS - 1);
		watch.onTick(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS);

		assertFalse(watch.shown(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS));
		watch.onTick(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS + 1);
		assertTrue(watch.shown(GameViewWatch.WELCOME_SCREEN_TIMEOUT_TICKS + 1));
	}
}
