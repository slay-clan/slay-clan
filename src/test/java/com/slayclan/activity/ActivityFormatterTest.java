package com.slayclan.activity;

import com.google.gson.Gson;
import com.slayclan.discord.PostPayload;
import com.slayclan.trigger.TimedLine.Kind;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ActivityFormatterTest
{
	private static final Instant AT = Instant.parse("2026-10-16T20:00:00Z");

	private final Gson gson = new Gson();
	private final ActivityFormatter formatter = new ActivityFormatter(gson);

	@Test
	public void clogEventListsItemIdQuantityAndSlots()
	{
		PostPayload post = formatter.clogEvent("Iron Clanmate", "Vorki", 21992, 1, 141, AT);

		assertEquals("clog", post.getKind());
		assertEquals("Iron Clanmate", post.getAccount());
		assertEquals("2026-10-16T20:00:00Z", post.getAt());
		assertEquals(Map.of("Item", "Vorki", "Item id", "21992", "Quantity", "1", "Log slots", "141"), post.getFields());
		assertNull(post.getEntries());
	}

	@Test
	public void caEventListsTaskIdAndTier()
	{
		PostPayload post = formatter.caEvent("Clanmate", "Into the Den of Giants", 398, "Easy", AT);

		assertEquals("ca", post.getKind());
		assertEquals(Map.of("Task", "Into the Den of Giants", "Task id", "398", "Tier", "Easy"), post.getFields());
	}

	@Test
	public void statusListsCountsPointsAndSlots()
	{
		CombatAchievementStatus ca = new CombatAchievementStatus(new int[]{41, 38, 22, 9, 3, 0}, new int[]{2, 2, 2, 0, 0, 0});
		CollectionLogStatus clog = new CollectionLogStatus(141, 1717, new int[]{60, 10, 20, 30, 21}, new int[]{600, 100, 200, 300, 517});

		PostPayload post = formatter.status("Clanmate", ca, clog, AT);

		assertEquals("status", post.getKind());
		assertEquals(Map.of(
			"CA completed", "41/38/22/9/3/0",
			"CA points", "234",
			"CA tier status", "2/2/2/0/0/0",
			"Log slots", "141/1717",
			"Log categories", "60/600, 10/100, 20/200, 30/300, 21/517"), post.getFields());
	}

	@Test
	public void clogSnapshotCarriesTheItemsInline()
	{
		ActivityFormatter.Snapshot snapshot = formatter.clogSnapshot("Clanmate", 42L, List.of(new OwnedItem(21992, "Vorki", 1)), AT);

		PostPayload post = snapshot.getPayload();
		assertEquals("clog-snapshot", post.getKind());
		assertEquals("42", post.getAccountHash());
		assertEquals("2026-10-16T20:00:00Z", post.getAt());
		assertEquals(Map.of("Items", "1"), post.getFields());
		assertEquals("[{\"id\":21992,\"name\":\"Vorki\",\"quantity\":1}]", gson.toJson(post.getEntries()));
	}

	@Test
	public void caSnapshotCarriesTheTasksInline()
	{
		ActivityFormatter.Snapshot snapshot = formatter.caSnapshot("Clanmate", 42L,
			List.of(new CompletedAchievement(398, "Into the Den of Giants", "Easy")), AT);

		assertEquals("ca-snapshot", snapshot.getPayload().getKind());
		assertEquals("[{\"id\":398,\"name\":\"Into the Den of Giants\",\"tier\":\"Easy\"}]", gson.toJson(snapshot.getPayload().getEntries()));
	}

	@Test
	public void pbSnapshotCarriesTheStoredBestsInline()
	{
		ActivityFormatter.Snapshot snapshot = formatter.pbSnapshot("Clanmate", 42L, List.of(new StoredPersonalBest("zulrah", 55.0, "0:55")), AT);

		assertEquals("pb-snapshot", snapshot.getPayload().getKind());
		assertEquals("[{\"boss\":\"zulrah\",\"seconds\":55.0,\"time\":\"0:55\"}]", gson.toJson(snapshot.getPayload().getEntries()));
	}

	@Test
	public void snapshotsTakenAtDifferentTimesWithTheSameEntriesShareAFingerprint()
	{
		List<StoredPersonalBest> bests = List.of(new StoredPersonalBest("zulrah", 55.0, "0:55"));

		assertEquals(formatter.pbSnapshot("Clanmate", 42L, bests, AT).getFingerprint(),
			formatter.pbSnapshot("Clanmate", 42L, bests, AT.plusSeconds(3600)).getFingerprint());
	}

	@Test
	public void personalBestListsBossTimeTypeTeamSizePartyAndNewFlag()
	{
		PostPayload post = formatter.personalBest("Clanmate", "Chambers of Xeric Challenge Mode", "29:49.20",
			Kind.SINGLE, 3, List.of("Clanmate", "Raidbuddy", "Third"), PersonalBestClaim.NEW_RECORD, AT);

		assertEquals("pb", post.getKind());
		assertEquals(Map.of(
			"Boss", "Chambers of Xeric Challenge Mode",
			"Time", "29:49.20",
			"Time type", "single",
			"Team size", "3",
			"Party", "Clanmate, Raidbuddy, Third",
			"New", "yes"), post.getFields());
	}

	@Test
	public void fastestKillAtAScaleIsNotANewRecord()
	{
		PostPayload post = formatter.personalBest("Clanmate", "Yama", "5:57", Kind.SINGLE, 1, List.of("Clanmate"),
			PersonalBestClaim.FASTEST_AT_SCALE, AT);

		assertEquals("no", post.getFields().get("New"));
	}

	@Test
	public void currentBossBestClaimsNoScaleSinceTheGameKeepsOnePerBoss()
	{
		PostPayload post = formatter.currentBossBest("Clanmate", "Yama", "3:09", Kind.SINGLE, AT);

		assertEquals(Map.of(
			"Boss", "Yama",
			"Time", "3:09",
			"Time type", "single",
			"Scale", "unknown, the game keeps one personal best per boss",
			"New", "no"), post.getFields());
		assertFalse(post.getFields().containsKey("Team size"));
	}

	@Test
	public void onlyACurrentBestReportedForTheBoardIsQuietInTheChat()
	{
		assertTrue(formatter.currentBossBest("Clanmate", "Yama", "3:09", Kind.SINGLE, AT).isReceiptQuiet());
		assertTrue(formatter.personalBest("Clanmate", "Zulrah", "0:55", Kind.SINGLE, 1, List.of("Clanmate"), PersonalBestClaim.CURRENT, AT)
			.isReceiptQuiet());
		assertFalse(formatter.personalBest("Clanmate", "Zulrah", "0:55", Kind.SINGLE, 1, List.of("Clanmate"), PersonalBestClaim.FASTEST_AT_SCALE, AT)
			.isReceiptQuiet());
		assertTrue(formatter.clogEvent("Clanmate", "Vorki", 21992, 1, 141, AT).isReceiptQuiet());
	}
}
