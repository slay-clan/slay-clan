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
		watch.onLogin();
		for (int tick = 0; tick < 20; tick++)
		{
			watch.onTick(tick);
		}

		assertFalse(watch.shown(19));
	}

	@Test
	public void thePlayerIsInViewAMomentAfterTheWelcomeScreenCloses()
	{
		watch.onLogin();
		watch.onTick(0);
		welcomeScreenOpen.set(false);
		watch.onTick(10);
		watch.onTick(11);

		assertFalse(watch.shown(11));
		watch.onTick(12);
		assertTrue(watch.shown(12));
	}

	@Test
	public void aMemberWhoStaysAwayFromKeyboardOnTheWelcomeScreenKeepsThePostWaiting()
	{
		watch.onLogin();
		for (int tick = 0; tick <= 6000; tick++)
		{
			watch.onTick(tick);
		}

		assertFalse(watch.shown(6000));
	}

	@Test
	public void aNewLoginWaitsForItsOwnWelcomeScreenToClose()
	{
		watch.onLogin();
		welcomeScreenOpen.set(false);
		watch.onTick(0);
		watch.onTick(5);
		assertTrue(watch.shown(5));

		watch.onLogin();
		welcomeScreenOpen.set(true);
		watch.onTick(51);

		assertFalse(watch.shown(55));
	}

	@Test
	public void aWelcomeScreenThatOpensAFewTicksAfterLoginStillHoldsThePost()
	{
		watch.onLogin();
		welcomeScreenOpen.set(false);
		watch.onTick(0);
		watch.onTick(1);
		welcomeScreenOpen.set(true);
		watch.onTick(2);
		watch.onTick(5);

		assertFalse(watch.shown(5));
	}
}
