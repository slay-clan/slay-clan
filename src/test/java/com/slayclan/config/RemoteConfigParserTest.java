package com.slayclan.config;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RemoteConfigParserTest
{
	private final RemoteConfigParser parser = new RemoteConfigParser(new Gson());

	@Test
	public void parsesValidConfig()
	{
		RemoteConfig config = parser.parse(TestConfigs.VALID_JSON).orElseThrow();

		assertEquals("SlayClan", config.getClanName());
		assertEquals("https://discord.com/api/webhooks/1/abc", config.getPointsWebhookUrl());
		assertEquals(1, config.getTasks().size());
		TaskDefinition task = config.getTasks().get(0);
		assertEquals("cox", task.getId());
		assertEquals("Clan Raid", task.getName());
		assertEquals("RAID:Chambers of Xeric", task.getTrigger());
		assertEquals("Repeatable", task.getTier());
		assertEquals(10, task.getPoints());
		assertEquals(2, task.getMinPlayers());
	}

	@Test
	public void readsTheContentTheClanHasSwitchedOff()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"ignoredContent\":[\"Fractured Archive\",\"Yama*\"],"
			+ "\"tasks\":[{\"id\":\"cox\",\"name\":\"Clan Raid\",\"trigger\":\"RAID:Chambers of Xeric\",\"tier\":\"Repeatable\",\"points\":10}]}";

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertTrue(config.ignores("fractured archive"));
		assertTrue(config.ignores("Yama (Awakened)"));
		assertFalse(config.ignores("Chambers of Xeric"));
	}

	@Test
	public void acceptsASingleSwitchedOffNameWrittenWithoutBrackets()
	{
		String json = TestConfigs.VALID_JSON.replace("\"tasks\"", "\"ignoredContent\":\"Fractured Archive\",\"tasks\"");

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertTrue(config.ignores("Fractured Archive"));
		assertFalse(config.ignores("Chambers of Xeric"));
	}

	@Test
	public void ignoresNothingWhenTheListIsMissing()
	{
		RemoteConfig config = parser.parse(TestConfigs.VALID_JSON).orElseThrow();

		assertFalse(config.ignores("Fractured Archive"));
	}

	@Test
	public void defaultsMinPlayersToOneWhenMissing()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":[{\"id\":\"cox\",\"name\":\"Clan Raid\",\"trigger\":\"RAID:Chambers of Xeric\",\"tier\":\"Repeatable\",\"points\":10}]}";

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertEquals(1, config.getTasks().get(0).getMinPlayers());
		assertFalse(config.getTasks().get(0).isIncludeStackable());
	}

	@Test
	public void parsesDropValueFields()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":[{\"id\":\"drops\",\"name\":\"Valuable drop\",\"trigger\":\"DROP\",\"tier\":\"Repeatable\","
			+ "\"minValue\":10000,\"valuePerPoint\":10000,\"includeStackable\":true}]}";

		TaskDefinition task = parser.parse(json).orElseThrow().getTasks().get(0);

		assertEquals(10_000, task.getMinValue());
		assertEquals(10_000, task.getValuePerPoint());
		assertTrue(task.isIncludeStackable());
	}

	@Test
	public void parsesCollectionLogTaskFields()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":[{\"id\":\"dust\",\"name\":\"Dust to Dust\",\"trigger\":\"CLOG\",\"tier\":\"PvM\",\"points\":200,"
			+ "\"once\":true,\"items\":[\"Sanguine dust\",\"Metamorphic dust\"],\"required\":2}]}";

		TaskDefinition task = parser.parse(json).orElseThrow().getTasks().get(0);

		assertTrue(task.isOnce());
		assertFalse(task.isClanOnly());
		assertEquals(2, task.getItems().size());
		assertEquals(2, task.getRequired());
	}

	@Test
	public void parsesTheExampleConfigShippedInDocs() throws Exception
	{
		String json = new String(Files.readAllBytes(Paths.get("docs", "example-config.json")), StandardCharsets.UTF_8);

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertEquals(53, config.getTasks().size());
		assertTrue(config.getTasks().stream().allMatch(task -> task.getTier() != null));
	}

	@Test
	public void readsTasksGroupedByTier()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":{"
			+ "\"Social\":[{\"id\":\"trial-by-fire\",\"trigger\":\"CLOG\"}],"
			+ "\"Valuable drops\":[{\"id\":\"drops\",\"trigger\":\"DROP\",\"tier\":\"Repeatable\"}]"
			+ "}}";

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertEquals(2, config.getTasks().size());
		assertEquals("Social", config.getTasks().get(0).getTier());
		assertEquals("Repeatable", config.getTasks().get(1).getTier());
	}

	@Test
	public void rejectsGroupThatIsNotAList()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":{\"Social\":{\"id\":\"trial-by-fire\"}}}";

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void allowsLineCommentsAsSectionHeadings()
	{
		String json = "{\n"
			+ "  \"clanName\": \"SlayClan\",\n"
			+ "  \"pointsWebhookUrl\": \"https://discord.com/api/webhooks/1/abc\",\n"
			+ "  \"tasks\": [\n"
			+ "    // Social\n"
			+ "    { \"id\": \"trial-by-fire\", \"trigger\": \"CLOG\" },\n"
			+ "    // Valuable drops\n"
			+ "    { \"id\": \"drops\", \"trigger\": \"DROP\" }\n"
			+ "  ]\n"
			+ "}";

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertEquals(2, config.getTasks().size());
		assertEquals("drops", config.getTasks().get(1).getId());
	}

	@Test
	public void acceptsLegacyDiscordappDomain()
	{
		String json = TestConfigs.VALID_JSON.replace("https://discord.com/", "https://discordapp.com/");

		assertEquals("SlayClan", parser.parse(json).orElseThrow().getClanName());
	}

	@Test
	public void rejectsMalformedJson()
	{
		assertFalse(parser.parse("{not json").isPresent());
	}

	@Test
	public void rejectsEmptyBody()
	{
		assertFalse(parser.parse("").isPresent());
	}

	@Test
	public void rejectsMissingClanName()
	{
		String json = TestConfigs.VALID_JSON.replace("\"clanName\":\"SlayClan\",", "");

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void rejectsWebhookOutsideDiscord()
	{
		String json = TestConfigs.VALID_JSON.replace("https://discord.com/api/webhooks/1/abc", "https://evil.example/hook");

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void readsOptionalActivityAndPbWebhooks()
	{
		String json = TestConfigs.VALID_JSON.replace("\"tasks\"",
			"\"activityWebhookUrl\":\"https://discord.com/api/webhooks/2/def\",\"pbWebhookUrl\":\"https://discord.com/api/webhooks/3/ghi\",\"tasks\"");

		RemoteConfig config = parser.parse(json).orElseThrow();

		assertEquals("https://discord.com/api/webhooks/2/def", config.getActivityWebhookUrl());
		assertEquals("https://discord.com/api/webhooks/3/ghi", config.getPbWebhookUrl());
	}

	@Test
	public void leavesActivityAndPbWebhooksNullWhenAbsent()
	{
		RemoteConfig config = parser.parse(TestConfigs.VALID_JSON).orElseThrow();

		assertNull(config.getActivityWebhookUrl());
		assertNull(config.getPbWebhookUrl());
	}

	@Test
	public void rejectsActivityWebhookOutsideDiscord()
	{
		String json = TestConfigs.VALID_JSON.replace("\"tasks\"", "\"activityWebhookUrl\":\"https://evil.example/hook\",\"tasks\"");

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void rejectsTaskWithoutId()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":[{\"name\":\"Clan Raid\",\"trigger\":\"RAID:*\"}]}";

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void rejectsTaskIdWithColon()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":[{\"id\":\"ca:safe\",\"trigger\":\"CA:Just To Be Safe\"}]}";

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void rejectsDuplicateTaskIds()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\","
			+ "\"tasks\":{\"Social\":[{\"id\":\"cox\",\"trigger\":\"RAID:*\"}],\"PvM\":[{\"id\":\"cox\",\"trigger\":\"KILL:Yama\"}]}}";

		assertFalse(parser.parse(json).isPresent());
	}

	@Test
	public void rejectsMissingTasks()
	{
		String json = "{\"clanName\":\"SlayClan\",\"pointsWebhookUrl\":\"https://discord.com/api/webhooks/1/abc\"}";

		Optional<RemoteConfig> result = parser.parse(json);

		assertFalse(result.isPresent());
	}
}
