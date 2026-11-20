package com.slayclan.activity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ToaTotalBestStoreTest
{
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final Map<String, String> memory = new HashMap<>();
	private final ToaTotalBestStore store = new ToaTotalBestStore(configManager);

	@Before
	public void setUp()
	{
		when(configManager.getConfiguration(anyString(), anyString()))
			.thenAnswer(invocation -> memory.get(invocation.getArgument(0) + "." + invocation.getArgument(1)));
		doAnswer(invocation -> memory.put(invocation.getArgument(0) + "." + invocation.getArgument(1), invocation.getArgument(2)))
			.when(configManager).setConfiguration(anyString(), anyString(), anyString());
	}

	@Test
	public void runeLitesToaChallengeTimesAreLeftOutWhileOtherBossesStay()
	{
		List<StoredPersonalBest> runeLite = List.of(
			new StoredPersonalBest("zulrah", 55.0, "0:55"),
			new StoredPersonalBest("tombs of amascut expert mode 3 players", 1235.4, "20:35.40"),
			new StoredPersonalBest("tombs of amascut entry mode solo", 1500.0, "25:00"),
			new StoredPersonalBest("tombs of amascut 8 players", 1400.0, "23:20"),
			new StoredPersonalBest("theatre of blood 4 players", 1100.0, "18:20"));

		assertEquals(List.of(new StoredPersonalBest("zulrah", 55.0, "0:55"), new StoredPersonalBest("theatre of blood 4 players", 1100.0, "18:20")),
			store.withTotalsInsteadOfChallengeTimes(42L, runeLite));
	}

	@Test
	public void aTotalRecordIsSentAsATotalEntryForItsModeAndTeamSize()
	{
		store.rememberIfFaster(42L, "Tombs of Amascut: Expert Mode", 3, 1797.6);
		store.rememberIfFaster(42L, "Tombs of Amascut", 1, 1500.0);

		assertEquals(List.of(
				new StoredPersonalBest("tombs of amascut solo total", 1500.0, "25:00"),
				new StoredPersonalBest("tombs of amascut expert mode 3 players total", 1797.6, "29:57.60")),
			store.withTotalsInsteadOfChallengeTimes(42L, List.of()));
	}

	@Test
	public void theFastestTotalIsKeptWhenASlowerRecordIsSeenLater()
	{
		store.rememberIfFaster(42L, "Tombs of Amascut: Entry Mode", 2, 1500.0);
		store.rememberIfFaster(42L, "Tombs of Amascut: Entry Mode", 2, 1600.0);
		store.rememberIfFaster(42L, "Tombs of Amascut: Entry Mode", 2, 1450.0);

		assertEquals(List.of(new StoredPersonalBest("tombs of amascut entry mode 2 players total", 1450.0, "24:10")),
			store.withTotalsInsteadOfChallengeTimes(42L, List.of()));
	}

	@Test
	public void eachAccountOnTheProfileKeepsItsOwnTotals()
	{
		store.rememberIfFaster(42L, "Tombs of Amascut", 2, 1500.0);

		assertTrue(store.withTotalsInsteadOfChallengeTimes(7L, List.of()).isEmpty());
	}

	@Test
	public void aTotalFromAnotherRaidOrAnImpossibleTeamSizeIsNotRemembered()
	{
		store.rememberIfFaster(42L, "Theatre of Blood", 3, 1500.0);
		store.rememberIfFaster(42L, "Tombs of Amascut", 0, 1500.0);
		store.rememberIfFaster(42L, "Tombs of Amascut", 9, 1500.0);

		assertTrue(memory.isEmpty());
	}

	@Test
	public void anUnreadableRememberedTotalIsLeftOutAndReplacedByTheNextRecord()
	{
		memory.put("slayclanmemory.toatotal.42.tombs-of-amascut-solo-total", "garbage");

		assertTrue(store.withTotalsInsteadOfChallengeTimes(42L, List.of()).isEmpty());

		store.rememberIfFaster(42L, "Tombs of Amascut", 1, 1500.0);

		assertEquals("1500.0", memory.get("slayclanmemory.toatotal.42.tombs-of-amascut-solo-total"));
	}
}
