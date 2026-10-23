package com.slayclan.activity;

import com.slayclan.discord.PostKind;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeliveredSnapshotsTest
{
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final DeliveredSnapshots delivered = new DeliveredSnapshots(configManager);

	@Test
	public void aSnapshotIsDeliveredWhenItsContentMatchesTheRememberedOne()
	{
		when(configManager.getConfiguration("slayclanmemory", "snapshot.clog_snapshot.42")).thenReturn("abc");

		assertTrue(delivered.isDelivered(PostKind.CLOG_SNAPSHOT, 42L, "abc"));
		assertFalse(delivered.isDelivered(PostKind.CLOG_SNAPSHOT, 42L, "def"));
		assertFalse(delivered.isDelivered(PostKind.CLOG_SNAPSHOT, 43L, "abc"));
	}

	@Test
	public void rememberedContentIsStoredPerKindAndAccount()
	{
		delivered.markDelivered(PostKind.PB_SNAPSHOT, 42L, "abc");

		verify(configManager).setConfiguration("slayclanmemory", "snapshot.pb_snapshot.42", "abc");
	}

	@Test
	public void theSameContentGivesTheSameFingerprintAndDifferentContentAnother()
	{
		assertEquals(DeliveredSnapshots.fingerprint("[1,2]"), DeliveredSnapshots.fingerprint("[1,2]"));
		assertNotEquals(DeliveredSnapshots.fingerprint("[1,2]"), DeliveredSnapshots.fingerprint("[1,3]"));
		assertEquals(64, DeliveredSnapshots.fingerprint("").length());
	}
}
