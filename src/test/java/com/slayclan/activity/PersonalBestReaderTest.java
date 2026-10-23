package com.slayclan.activity;

import java.util.List;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PersonalBestReaderTest
{
	@Test
	public void listsEveryStoredBestWithItsTime()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfigurationKeys("personalbest", "rsprofile.abc", "")).thenReturn(List.of("zulrah", "yama"));
		when(configManager.getRSProfileConfiguration("personalbest", "zulrah", double.class)).thenReturn(55.0);
		when(configManager.getRSProfileConfiguration("personalbest", "yama", double.class)).thenReturn(189.0);

		assertEquals(List.of(new StoredPersonalBest("zulrah", 55.0, "0:55"), new StoredPersonalBest("yama", 189.0, "3:09")),
			new PersonalBestReader(configManager).read());
	}

	@Test
	public void returnsNothingBeforeTheProfileIsKnown()
	{
		ConfigManager configManager = mock(ConfigManager.class);

		assertTrue(new PersonalBestReader(configManager).read().isEmpty());
	}
}
