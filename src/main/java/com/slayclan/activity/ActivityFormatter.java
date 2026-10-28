package com.slayclan.activity;

import com.google.gson.Gson;
import com.slayclan.discord.MessageFormatter;
import com.slayclan.discord.Nonce;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.PostPayload;
import com.slayclan.trigger.TimedLine;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Builds the activity feed and PB posts. Field names are the bot's contract.
 */
public class ActivityFormatter
{
	@Value
	public static class Snapshot
	{
		PostPayload payload;
		String fingerprint;
	}

	private final Gson gson;

	public ActivityFormatter(Gson gson)
	{
		this.gson = gson;
	}

	public PostPayload clogEvent(String account, String item, int itemId, int quantity, int logSlots, Instant at)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Item", item);
		fields.put("Item id", String.valueOf(itemId));
		fields.put("Quantity", String.valueOf(quantity));
		fields.put("Log slots", String.valueOf(logSlots));
		return MessageFormatter.payload(PostKind.CLOG, account, fields, at);
	}

	public PostPayload caEvent(String account, String task, int taskId, String tier, Instant at)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Task", task);
		fields.put("Task id", String.valueOf(taskId));
		fields.put("Tier", tier);
		return MessageFormatter.payload(PostKind.CA, account, fields, at);
	}

	public PostPayload status(String account, CombatAchievementStatus ca, CollectionLogStatus clog, Instant at)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("CA completed", ca.completedText());
		fields.put("CA points", String.valueOf(ca.points()));
		fields.put("CA tier status", ca.tierStatusText());
		fields.put("Log slots", clog.slotsText());
		fields.put("Log categories", clog.categoriesText());
		return MessageFormatter.payload(PostKind.STATUS, account, fields, at);
	}

	public Snapshot caSnapshot(String account, long accountHash, List<CompletedAchievement> tasks, Instant at)
	{
		return snapshot(PostKind.CA_SNAPSHOT, account, accountHash, tasks, at);
	}

	public Snapshot clogSnapshot(String account, long accountHash, List<OwnedItem> items, Instant at)
	{
		return snapshot(PostKind.CLOG_SNAPSHOT, account, accountHash, items, at);
	}

	public Snapshot pbSnapshot(String account, long accountHash, List<StoredPersonalBest> bests, Instant at)
	{
		return snapshot(PostKind.PB_SNAPSHOT, account, accountHash, bests, at);
	}

	/**
	 * The party is the clan members who fought, so the bot can give every one of them their leaderboard row at once
	 * (decided 2026-10-22); only all-clan teams reach this point, so no outsider's name leaves the client.
	 */
	public PostPayload personalBest(String account, String boss, String time, TimedLine.Kind kind, int teamSize, List<String> party,
		PersonalBestClaim claim, Instant at)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Boss", boss);
		fields.put("Time", time);
		fields.put("Time type", timeType(kind));
		fields.put("Team size", String.valueOf(teamSize));
		fields.put("Party", String.join(", ", party));
		fields.put("New", claim.isNewRecord() ? "yes" : "no");
		return personalBestPayload(account, fields, claim, at);
	}

	/**
	 * The game keeps one personal best per boss across team sizes (raids are per size), so a current best reported
	 * at a later kill may come from another scale than today's party. No team size or party is claimed for it.
	 */
	public PostPayload currentBossBest(String account, String boss, String time, TimedLine.Kind kind, Instant at)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Boss", boss);
		fields.put("Time", time);
		fields.put("Time type", timeType(kind));
		fields.put("Scale", "unknown, the game keeps one personal best per boss");
		fields.put("New", "no");
		return personalBestPayload(account, fields, PersonalBestClaim.CURRENT, at);
	}

	// A current best is reported so the board can be filled in, not because of this kill, so it gets no chat line
	private static PostPayload personalBestPayload(String account, Map<String, String> fields, PersonalBestClaim claim, Instant at)
	{
		return new PostPayload(PostKind.PB.getName(), Nonce.next(), account, null, at.toString(), fields, null,
			claim == PersonalBestClaim.CURRENT);
	}

	private static String timeType(TimedLine.Kind kind)
	{
		return kind.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The fingerprint covers the entries only, so a snapshot taken later with the same content counts as unchanged.
	 */
	private Snapshot snapshot(PostKind kind, String account, long accountHash, List<?> entries, Instant at)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Items", String.valueOf(entries.size()));
		PostPayload payload = new PostPayload(kind.getName(), Nonce.next(), account, String.valueOf(accountHash), at.toString(),
			fields, entries, false);
		return new Snapshot(payload, DeliveredSnapshots.fingerprint(gson.toJson(entries)));
	}
}
