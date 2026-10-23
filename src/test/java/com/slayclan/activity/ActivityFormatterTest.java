package com.slayclan.activity;

import com.google.gson.Gson;
import com.slayclan.discord.WebhookPayload;
import com.slayclan.trigger.TimedLine.Kind;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ActivityFormatterTest
{
	private static final Instant AT = Instant.parse("2026-10-16T20:00:00Z");

	private final ActivityFormatter formatter = new ActivityFormatter(new Gson());

	@Test
	public void clogEventListsItemIdQuantityAndSlots()
	{
		WebhookPayload.Embed embed = formatter.clogEvent("Clanmate", "Iron Clanmate", "Vorki", 21992, 1, 141, AT).getEmbeds().get(0);

		assertEquals("Clanmate", embed.getTitle());
		assertEquals(List.of(
			new WebhookPayload.Field("Account", "Iron Clanmate", true),
			new WebhookPayload.Field("Item", "Vorki", true),
			new WebhookPayload.Field("Item id", "21992", true),
			new WebhookPayload.Field("Quantity", "1", true),
			new WebhookPayload.Field("Log slots", "141", true)), embed.getFields());
		assertTrue(embed.getFooter().getText().startsWith("slayclan:clog:"));
		assertNull(embed.getImage());
	}

	@Test
	public void caEventListsTaskIdAndTier()
	{
		WebhookPayload.Embed embed = formatter.caEvent("Clanmate", "Clanmate", "Into the Den of Giants", 398, "Easy", AT).getEmbeds().get(0);

		assertEquals(List.of(
			new WebhookPayload.Field("Account", "Clanmate", true),
			new WebhookPayload.Field("Task", "Into the Den of Giants", true),
			new WebhookPayload.Field("Task id", "398", true),
			new WebhookPayload.Field("Tier", "Easy", true)), embed.getFields());
		assertTrue(embed.getFooter().getText().startsWith("slayclan:ca:"));
	}

	@Test
	public void statusListsCountsPointsAndSlots()
	{
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[]{41, 38, 22, 9, 3, 0}, new int[]{2, 2, 2, 0, 0, 0});
		CollectionLogStatus clog = new CollectionLogStatus(141, 1717, new int[]{60, 10, 20, 30, 21}, new int[]{600, 100, 200, 300, 517});

		WebhookPayload.Embed embed = formatter.status("Clanmate", "Clanmate", ca, clog, AT).getEmbeds().get(0);

		assertEquals(List.of(
			new WebhookPayload.Field("Account", "Clanmate", true),
			new WebhookPayload.Field("CA completed", "41/38/22/9/3/0", true),
			new WebhookPayload.Field("CA points", "234", true),
			new WebhookPayload.Field("CA tier status", "2/2/2/0/0/0", true),
			new WebhookPayload.Field("Log slots", "141/1717", true),
			new WebhookPayload.Field("Log categories", "60/600, 10/100, 20/200, 30/300, 21/517", false)), embed.getFields());
		assertTrue(embed.getFooter().getText().startsWith("slayclan:status:"));
	}

	@Test
	public void clogSnapshotAttachesTheItemsAsJson()
	{
		ActivityFormatter.Snapshot snapshot = formatter.clogSnapshot("Clanmate", "Clanmate", 42L,
			List.of(new OwnedItem(21992, "Vorki", 1)), AT);

		assertEquals("SPOILER_clog-snapshot.json", snapshot.getAttachment().getFilename());
		String json = new String(snapshot.getAttachment().getBytes(), StandardCharsets.UTF_8);
		assertEquals("{\"account\":\"Clanmate\",\"accountHash\":\"42\",\"at\":\"2026-10-16T20:00:00Z\",\"items\":[{\"id\":21992,\"name\":\"Vorki\",\"quantity\":1}]}", json);
		WebhookPayload.Embed embed = snapshot.getPayload().getEmbeds().get(0);
		assertEquals(new WebhookPayload.Field("Items", "1", true), embed.getFields().get(1));
		assertTrue(embed.getFooter().getText().startsWith("slayclan:clog-snapshot:"));
	}

	@Test
	public void caSnapshotAttachesTheTasksAsJson()
	{
		ActivityFormatter.Snapshot snapshot = formatter.caSnapshot("Clanmate", "Clanmate", 42L,
			List.of(new CompletedAchievement(398, "Into the Den of Giants", "Easy")), AT);

		assertEquals("SPOILER_ca-snapshot.json", snapshot.getAttachment().getFilename());
		String json = new String(snapshot.getAttachment().getBytes(), StandardCharsets.UTF_8);
		assertEquals("{\"account\":\"Clanmate\",\"accountHash\":\"42\",\"at\":\"2026-10-16T20:00:00Z\",\"tasks\":[{\"id\":398,\"name\":\"Into the Den of Giants\",\"tier\":\"Easy\"}]}", json);
		assertTrue(snapshot.getPayload().getEmbeds().get(0).getFooter().getText().startsWith("slayclan:ca-snapshot:"));
	}

	@Test
	public void pbSnapshotAttachesTheStoredBestsAsJson()
	{
		ActivityFormatter.Snapshot snapshot = formatter.pbSnapshot("Clanmate", "Clanmate", 42L,
			List.of(new StoredPersonalBest("zulrah", 55.0, "0:55")), AT);

		assertEquals("SPOILER_pb-snapshot.json", snapshot.getAttachment().getFilename());
		String json = new String(snapshot.getAttachment().getBytes(), StandardCharsets.UTF_8);
		assertEquals("{\"account\":\"Clanmate\",\"accountHash\":\"42\",\"at\":\"2026-10-16T20:00:00Z\",\"bests\":[{\"boss\":\"zulrah\",\"seconds\":55.0,\"time\":\"0:55\"}]}", json);
		assertTrue(snapshot.getPayload().getEmbeds().get(0).getFooter().getText().startsWith("slayclan:pb-snapshot:"));
	}

	@Test
	public void snapshotsTakenAtDifferentTimesWithTheSameEntriesShareAFingerprint()
	{
		List<StoredPersonalBest> bests = List.of(new StoredPersonalBest("zulrah", 55.0, "0:55"));

		assertEquals(formatter.pbSnapshot("Clanmate", "Clanmate", 42L, bests, AT).getFingerprint(),
			formatter.pbSnapshot("Clanmate", "Clanmate", 42L, bests, AT.plusSeconds(3600)).getFingerprint());
	}

	@Test
	public void personalBestListsBossTimeTypeTeamSizePartyAndNewFlag()
	{
		WebhookPayload.Embed embed = formatter.personalBest("Clanmate", "Clanmate", "Chambers of Xeric Challenge Mode", "29:49.20",
			Kind.SINGLE, 3, List.of("Clanmate", "Raidbuddy", "Third"), PersonalBestClaim.NEW_RECORD, true, AT).getEmbeds().get(0);

		assertEquals("Has set a new personal best", embed.getDescription());
		assertEquals(List.of(
			new WebhookPayload.Field("Account", "Clanmate", true),
			new WebhookPayload.Field("Boss", "Chambers of Xeric Challenge Mode", true),
			new WebhookPayload.Field("Time", "29:49.20", true),
			new WebhookPayload.Field("Time type", "single", true),
			new WebhookPayload.Field("Team size", "3", true),
			new WebhookPayload.Field("Party", "Clanmate, Raidbuddy, Third", false),
			new WebhookPayload.Field("New", "yes", true)), embed.getFields());
		assertEquals("attachment://screenshot.png", embed.getImage().getUrl());
		assertTrue(embed.getFooter().getText().startsWith("slayclan:pb:"));
	}

	@Test
	public void currentPersonalBestHasNoScreenshotAndSaysSo()
	{
		WebhookPayload.Embed embed = formatter.personalBest("Clanmate", "Clanmate", "Zulrah", "0:55",
			Kind.SINGLE, 1, List.of("Clanmate"), PersonalBestClaim.CURRENT, false, AT).getEmbeds().get(0);

		assertEquals("Current personal best", embed.getDescription());
		assertEquals(new WebhookPayload.Field("New", "no", true), embed.getFields().get(6));
		assertNull(embed.getImage());
	}

	@Test
	public void fastestKillAtAScaleSaysSoAndIsNotANewRecord()
	{
		WebhookPayload.Embed embed = formatter.personalBest("Clanmate", "Clanmate", "Yama", "5:57",
			Kind.SINGLE, 1, List.of("Clanmate"), PersonalBestClaim.FASTEST_AT_SCALE, true, AT).getEmbeds().get(0);

		assertEquals("Fastest kill at this scale since the plugin was installed", embed.getDescription());
		assertEquals(new WebhookPayload.Field("New", "no", true), embed.getFields().get(6));
		assertEquals("attachment://screenshot.png", embed.getImage().getUrl());
	}

	@Test
	public void currentBossBestClaimsNoScaleSinceTheGameKeepsOnePerBoss()
	{
		WebhookPayload.Embed embed = formatter.currentBossBest("Clanmate", "Clanmate", "Yama", "3:09", Kind.SINGLE, AT).getEmbeds().get(0);

		assertEquals("Current personal best", embed.getDescription());
		assertEquals(List.of(
			new WebhookPayload.Field("Account", "Clanmate", true),
			new WebhookPayload.Field("Boss", "Yama", true),
			new WebhookPayload.Field("Time", "3:09", true),
			new WebhookPayload.Field("Time type", "single", true),
			new WebhookPayload.Field("Scale", "unknown, the game keeps one personal best per boss", false),
			new WebhookPayload.Field("New", "no", true)), embed.getFields());
		assertNull(embed.getImage());
	}
}
