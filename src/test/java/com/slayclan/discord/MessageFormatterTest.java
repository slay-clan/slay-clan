package com.slayclan.discord;

import com.slayclan.config.TaskDefinition;
import com.slayclan.drop.ValuableDrop;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
	public void taskPostCarriesTheTaskFieldsTheAccountAndTheTime()
	{
		PostPayload post = formatter.build("Clanmate", clanRaid(), COMPLETED_AT);

		assertEquals("task", post.getKind());
		assertEquals("Clanmate", post.getAccount());
		assertEquals("2026-10-10T18:00:00Z", post.getAt());
		assertEquals(List.of("Task", "Tier", "Points"), List.copyOf(post.getFields().keySet()));
		assertEquals(Map.of("Task", "Clan Raid", "Tier", "Repeatable", "Points", "10"), post.getFields());
		assertNull(post.getEntries());
	}

	@Test
	public void teamTaskNamesTheClanMembersInTheParty()
	{
		PostPayload post = formatter.build("Clanmate", clanRaid(), List.of("Clanmate", "Raidbuddy", "Third"), COMPLETED_AT);

		assertEquals("Clanmate, Raidbuddy, Third", post.getFields().get("Party"));
	}

	@Test
	public void soloTaskHasNoPartyField()
	{
		PostPayload post = formatter.build("Clanmate", clanRaid(), List.of("Clanmate"), COMPLETED_AT);

		assertFalse(post.getFields().containsKey("Party"));
	}

	@Test
	public void dropPostCarriesItemValueInMillionsPointsAndSource()
	{
		ValuableDrop drop = new ValuableDrop("Arcane prayer scroll", 1, 4_000_000, 4);

		PostPayload post = formatter.buildDrop("Clanmate", drop, "Great Olm", COMPLETED_AT);

		assertEquals("drop", post.getKind());
		assertEquals(Map.of("Item", "Arcane prayer scroll", "Value in M", "4", "Points", "4", "Source", "Great Olm"), post.getFields());
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
		return formatter.buildDrop("Clanmate", drop, "Lizardman shaman", COMPLETED_AT).getFields().get("Value in M");
	}

	@Test
	public void showsQuantityForStackDrop()
	{
		ValuableDrop drop = new ValuableDrop("Zulrah's scales", 500, 75_000, 7);

		PostPayload post = formatter.buildDrop("Clanmate", drop, "Zulrah", COMPLETED_AT);

		assertEquals("Zulrah's scales x500", post.getFields().get("Item"));
	}

	@Test
	public void addsItemAndSourceToItemTask()
	{
		TaskDefinition task = task("Jar Collector", "Repeatable", 30);

		PostPayload post = formatter.buildItemTask("Clanmate", task, "Jar of sand", "Kalphite Queen", COMPLETED_AT);

		assertEquals("Jar of sand", post.getFields().get("Item"));
		assertEquals("Kalphite Queen", post.getFields().get("Source"));
	}

	@Test
	public void omitsSourceWhenUnknown()
	{
		TaskDefinition task = task("New Pet, New Boss", "Repeatable", 200);

		PostPayload post = formatter.buildItemTask("Clanmate", task, "Vorki", null, COMPLETED_AT);

		assertEquals("Vorki", post.getFields().get("Item"));
		assertFalse(post.getFields().containsKey("Source"));
	}

	@Test
	public void twoPostsGetDifferentNonces()
	{
		String first = formatter.build("Clanmate", clanRaid(), COMPLETED_AT).getNonce();
		String second = formatter.build("Clanmate", clanRaid(), COMPLETED_AT).getNonce();

		assertTrue(first.matches("[0-9a-f]{16}"));
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
