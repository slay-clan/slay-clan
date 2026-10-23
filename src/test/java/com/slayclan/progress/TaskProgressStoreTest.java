package com.slayclan.progress;

import java.util.List;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskProgressStoreTest
{
	private ConfigManager configManager;
	private TaskProgressStore store;

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		store = new TaskProgressStore(configManager);
	}

	@Test
	public void reportsTaskAsNotCompletedByDefault()
	{
		assertFalse(store.isCompleted("fire-cape"));
	}

	@Test
	public void readsCompletedFlag()
	{
		when(configManager.getConfiguration("slayclanmemory", "completed.fire-cape")).thenReturn("true");

		assertTrue(store.isCompleted("fire-cape"));
	}

	@Test
	public void writesCompletedFlagToProfile()
	{
		store.markCompleted("fire-cape");

		verify(configManager).setConfiguration("slayclanmemory", "completed.fire-cape", "true");
	}

	@Test
	public void roundTripsLoggedItems()
	{
		store.saveLoggedItems("dust", Set.of("Sanguine dust", "Metamorphic dust"));

		verify(configManager).setConfiguration("slayclanmemory", "clog.dust", "Metamorphic dust|Sanguine dust");
	}

	@Test
	public void parsesLoggedItems()
	{
		when(configManager.getConfiguration("slayclanmemory", "clog.dust")).thenReturn("Metamorphic dust|Sanguine dust");

		assertEquals(Set.of("Sanguine dust", "Metamorphic dust"), store.loggedItems("dust"));
	}

	@Test
	public void returnsNoLoggedItemsWhenNothingStored()
	{
		assertTrue(store.loggedItems("dust").isEmpty());
	}

	@Test
	public void readsBaselineFlagPerAccount()
	{
		when(configManager.getConfiguration("slayclanmemory", "baseline.quest-cape.42")).thenReturn("true");

		assertTrue(store.isBaselined("quest-cape", 42));
		assertFalse(store.isBaselined("quest-cape", 43));
		assertFalse(store.isBaselined("achievement-cape", 42));
	}

	@Test
	public void writesBaselineFlagForTheAccount()
	{
		store.markBaselined("quest-cape", 42);

		verify(configManager).setConfiguration("slayclanmemory", "baseline.quest-cape.42", "true");
	}

}
