package com.slayclan.config;

public final class TestConfigs
{
	public static final String VALID_JSON = "{"
		+ "\"clanName\":\"SlayClan\","
		+ "\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
		+ "\"tasks\":[{\"id\":\"cox\",\"name\":\"Clan Raid\",\"trigger\":\"RAID:Chambers of Xeric\","
		+ "\"tier\":\"Repeatable\",\"points\":10,\"minPlayers\":2}]"
		+ "}";

	private TestConfigs()
	{
	}
}
