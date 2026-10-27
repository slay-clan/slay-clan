package com.slayclan.discord;

import com.slayclan.config.TaskDefinition;
import com.slayclan.drop.ValuableDrop;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MessageFormatterTest
{
	private static final Instant COMPLETED_AT = Instant.parse("2026-10-10T18:00:00Z");

	private final MessageFormatter formatter = new MessageFormatter();

	@Test
	public void buildsTaskEmbedWithDiscordNameAndFields()
	{
		WebhookPayload.Embed embed = formatter.build("Clanmate", "Clanmate", clanRaid(), COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals("Clanmate", embed.getTitle());
		assertEquals("Has completed the following clan task", embed.getDescription());
		assertEquals(List.of(
			new WebhookPayload.Field("Task", "Clan Raid", true),
			new WebhookPayload.Field("Tier", "Repeatable", true),
			new WebhookPayload.Field("Points", "10", true),
			new WebhookPayload.Field("Account", "Clanmate", true)), embed.getFields());
		assertEquals("2026-10-10T18:00:00Z", embed.getTimestamp());
		assertEquals("attachment://screenshot.png", embed.getImage().getUrl());
	}

	@Test
	public void teamTaskNamesTheClanMembersInTheParty()
	{
		WebhookPayload.Embed embed = formatter.build("Clanmate", "Clanmate", clanRaid(), List.of("Clanmate", "Raidbuddy", "Third"),
			COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals(new WebhookPayload.Field("Party", "Clanmate, Raidbuddy, Third", false), embed.getFields().get(4));
	}

	@Test
	public void soloTaskHasNoPartyField()
	{
		WebhookPayload.Embed embed = formatter.build("Clanmate", "Clanmate", clanRaid(), List.of("Clanmate"), COMPLETED_AT, true)
			.getEmbeds().get(0);

		assertEquals(4, embed.getFields().size());
	}

	@Test
	public void omitsImageAndSaysSoWithoutScreenshot()
	{
		WebhookPayload.Embed embed = formatter.build("Clanmate", "Clanmate", clanRaid(), COMPLETED_AT, false).getEmbeds().get(0);

		assertNull(embed.getImage());
		assertTrue(embed.getDescription().endsWith("\n\nScreenshot failed"));
	}

	@Test
	public void doesNotMentionScreenshotWhenItIsAttached()
	{
		WebhookPayload.Embed embed = formatter.build("Clanmate", "Clanmate", clanRaid(), COMPLETED_AT, true).getEmbeds().get(0);

		assertFalse(embed.getDescription().contains("Screenshot failed"));
	}

	@Test
	public void buildsValuableDropEmbedLikeTheBot()
	{
		ValuableDrop drop = new ValuableDrop("Arcane prayer scroll", 1, 4_000_000, 4);

		WebhookPayload.Embed embed = formatter.buildDrop("Clanmate", "Clanmate", drop, "Great Olm", COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals("Clanmate", embed.getTitle());
		assertEquals("Has received a valuable drop", embed.getDescription());
		assertEquals(List.of(
			new WebhookPayload.Field("Item", "Arcane prayer scroll", true),
			new WebhookPayload.Field("Value in M", "4", true),
			new WebhookPayload.Field("Points", "4", true),
			new WebhookPayload.Field("Source", "Great Olm", false),
			new WebhookPayload.Field("Account", "Clanmate", true)), embed.getFields());
	}

	@Test
	public void roundsValueToWholeMillions()
	{
		assertEquals("4", valueInMillions(4_400_000));
		assertEquals("5", valueInMillions(4_500_000));
		assertEquals("5", valueInMillions(4_800_000));
	}

	private String valueInMillions(long value)
	{
		ValuableDrop drop = new ValuableDrop("Dragon warhammer", 1, value, 0);
		return formatter.buildDrop("Clanmate", "Clanmate", drop, "Lizardman shaman", COMPLETED_AT, true).getEmbeds().get(0).getFields().get(1).getValue();
	}

	@Test
	public void showsQuantityForStackDrop()
	{
		ValuableDrop drop = new ValuableDrop("Zulrah's scales", 500, 75_000, 7);

		WebhookPayload.Embed embed = formatter.buildDrop("Clanmate", "Clanmate", drop, "Zulrah", COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals("Zulrah's scales x500", embed.getFields().get(0).getValue());
	}

	@Test
	public void addsItemAndSourceToItemTask()
	{
		TaskDefinition task = task("Jar Collector", "Repeatable", 30);

		WebhookPayload.Embed embed = formatter.buildItemTask("Clanmate", "Clanmate", task, "Jar of sand", "Kalphite Queen", COMPLETED_AT, true)
			.getEmbeds().get(0);

		assertEquals(new WebhookPayload.Field("Item", "Jar of sand", true), embed.getFields().get(3));
		assertEquals(new WebhookPayload.Field("Source", "Kalphite Queen", true), embed.getFields().get(4));
	}

	@Test
	public void omitsSourceWhenUnknown()
	{
		TaskDefinition task = task("New Pet, New Boss", "Repeatable", 200);

		WebhookPayload.Embed embed = formatter.buildItemTask("Clanmate", "Clanmate", task, "Vorki", null, COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals(5, embed.getFields().size());
		assertEquals("Vorki", embed.getFields().get(3).getValue());
	}

	@Test
	public void taskPostNamesTheAccountAndCarriesATaskFooter()
	{
		WebhookPayload.Embed embed = formatter.build("Clanmate", "Iron Clanmate", clanRaid(), COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals(new WebhookPayload.Field("Account", "Iron Clanmate", true), embed.getFields().get(3));
		assertTrue(embed.getFooter().getText().matches("slayclan:task:[0-9a-f]{16}"));
	}

	@Test
	public void dropPostCarriesADropFooter()
	{
		ValuableDrop drop = new ValuableDrop("Arcane prayer scroll", 1, 4_000_000, 4);

		WebhookPayload.Embed embed = formatter.buildDrop("Clanmate", "Clanmate", drop, "Great Olm", COMPLETED_AT, true).getEmbeds().get(0);

		assertEquals(new WebhookPayload.Field("Account", "Clanmate", true), embed.getFields().get(4));
		assertTrue(embed.getFooter().getText().startsWith("slayclan:drop:"));
	}

	@Test
	public void twoPostsGetDifferentNonces()
	{
		String first = formatter.build("Clanmate", "Clanmate", clanRaid(), COMPLETED_AT, true).getEmbeds().get(0).getFooter().getText();
		String second = formatter.build("Clanmate", "Clanmate", clanRaid(), COMPLETED_AT, true).getEmbeds().get(0).getFooter().getText();

		assertFalse(first.equals(second));
	}

	private static TaskDefinition clanRaid()
	{
		return task("Clan Raid", "Repeatable", 10);
	}

	private static TaskDefinition task(String name, String tier, int points)
	{
		TaskDefinition task = new TaskDefinition();
		task.setName(name);
		task.setTier(tier);
		task.setPoints(points);
		return task;
	}
}
