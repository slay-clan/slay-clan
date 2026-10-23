package com.slayclan.activity;

import com.slayclan.SlayClanConfig;
import com.slayclan.discord.PostKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import net.runelite.client.config.ConfigManager;

/**
 * Remembers the content of the last snapshot and status post Discord accepted per account, so that logins and
 * world hops only post when something changed. A failed delivery is not remembered and is tried again next time.
 */
public class DeliveredSnapshots
{
	private static final String PREFIX = "snapshot.";

	private final ConfigManager configManager;

	public DeliveredSnapshots(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public boolean isDelivered(PostKind kind, long accountHash, String fingerprint)
	{
		return fingerprint.equals(configManager.getConfiguration(SlayClanConfig.MEMORY_GROUP, key(kind, accountHash)));
	}

	public void markDelivered(PostKind kind, long accountHash, String fingerprint)
	{
		configManager.setConfiguration(SlayClanConfig.MEMORY_GROUP, key(kind, accountHash), fingerprint);
	}

	public static String fingerprint(String content)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : digest)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
		}
	}

	private static String key(PostKind kind, long accountHash)
	{
		return PREFIX + kind.name().toLowerCase(Locale.ROOT) + "." + accountHash;
	}
}
