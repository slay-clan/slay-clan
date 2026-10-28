package com.slayclan.config;

public final class TestConfigs
{
	public static final String API_URL = "https://bot.example/api/v1/posts";
	public static final String VALID_JSON = "{"
		+ "\"clanName\":\"SlayClan\","
		+ "\"apiUrl\":\"" + API_URL + "\","
		+ "\"tasks\":[{\"id\":\"cox\",\"name\":\"Clan Raid\",\"trigger\":\"RAID:Chambers of Xeric\","
		+ "\"tier\":\"Repeatable\",\"points\":10,\"minPlayers\":2}]"
		+ "}";

	private TestConfigs()
	{
	}
}
