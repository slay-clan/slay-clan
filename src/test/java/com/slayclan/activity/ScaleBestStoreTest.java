package com.slayclan.activity;

import com.slayclan.trigger.TimedLine.Kind;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ScaleBestStoreTest
{
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final ScaleBestStore store = new ScaleBestStore(configManager);

	@Test
	public void anyKillIsFasterThanNothingStored()
	{
		assertTrue(store.isFasterThanStored(42L, "Yama", Kind.SINGLE, 1, 357.0));
	}

	@Test
	public void onlyAFasterKillBeatsTheStoredTimeForThatScale()
	{
		when(configManager.getConfiguration("slayclanmemory", "scalebest.42.yama.single.1")).thenReturn("357.0");

		assertTrue(store.isFasterThanStored(42L, "Yama", Kind.SINGLE, 1, 340.0));
		assertFalse(store.isFasterThanStored(42L, "Yama", Kind.SINGLE, 1, 357.0));
		assertTrue(store.isFasterThanStored(42L, "Yama", Kind.SINGLE, 2, 357.0));
	}

	@Test
	public void storesTheBestUnderBossTimeTypeAndTeamSize()
	{
		store.saveBest(42L, "Theatre of Blood: Hard Mode", Kind.CHALLENGE, 3, 900.0);

		verify(configManager).setConfiguration("slayclanmemory", "scalebest.42.theatre-of-blood-hard-mode.challenge.3", "900.0");
	}

	@Test
	public void remembersWhichBossesHadTheirGameBestReported()
	{
		assertFalse(store.isGameBestReported(42L, "Yama"));

		store.markGameBestReported(42L, "Yama");

		verify(configManager).setConfiguration("slayclanmemory", "gamebestreported.42.yama", "true");
	}

}
