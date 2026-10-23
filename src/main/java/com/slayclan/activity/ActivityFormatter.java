package com.slayclan.activity;

import com.google.gson.Gson;
import com.slayclan.discord.Attachment;
import com.slayclan.discord.MessageFormatter;
import com.slayclan.discord.Nonce;
import com.slayclan.discord.PostKind;
import com.slayclan.discord.WebhookPayload;
import com.slayclan.trigger.TimedLine;
import java.time.Instant;
import java.util.ArrayList;
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
	private static final int EMBED_COLOR = 0x8B0000;

	@Value
	public static class Snapshot
	{
		WebhookPayload payload;
		Attachment attachment;
		String fingerprint;
	}

	private final Gson gson;

	public ActivityFormatter(Gson gson)
	{
		this.gson = gson;
	}

	public WebhookPayload clogEvent(String discordName, String account, String item, int itemId, int quantity, int logSlots, Instant at)
	{
		List<WebhookPayload.Field> fields = accountField(account);
		fields.add(new WebhookPayload.Field("Item", item, true));
		fields.add(new WebhookPayload.Field("Item id", String.valueOf(itemId), true));
		fields.add(new WebhookPayload.Field("Quantity", String.valueOf(quantity), true));
		fields.add(new WebhookPayload.Field("Log slots", String.valueOf(logSlots), true));
		return payload(discordName, "Has added an item to the collection log", fields, PostKind.CLOG, at, false);
	}

	public WebhookPayload caEvent(String discordName, String account, String task, int taskId, String tier, Instant at)
	{
		List<WebhookPayload.Field> fields = accountField(account);
		fields.add(new WebhookPayload.Field("Task", task, true));
		fields.add(new WebhookPayload.Field("Task id", String.valueOf(taskId), true));
		fields.add(new WebhookPayload.Field("Tier", tier, true));
		return payload(discordName, "Has completed a combat achievement", fields, PostKind.CA, at, false);
	}

	public WebhookPayload status(String discordName, String account, CombatAchievementStatus ca, CollectionLogStatus clog, Instant at)
	{
		List<WebhookPayload.Field> fields = accountField(account);
		fields.add(new WebhookPayload.Field("CA completed", ca.completedText(), true));
		fields.add(new WebhookPayload.Field("CA points", String.valueOf(ca.points()), true));
		fields.add(new WebhookPayload.Field("CA tier status", ca.tierStatusText(), true));
		fields.add(new WebhookPayload.Field("Log slots", clog.slotsText(), true));
		fields.add(new WebhookPayload.Field("Log categories", clog.categoriesText(), false));
		return payload(discordName, "Progress", fields, PostKind.STATUS, at, false);
	}

	public Snapshot caSnapshot(String discordName, String account, long accountHash, List<CompletedAchievement> tasks, Instant at)
	{
		return snapshot(discordName, account, accountHash, "Combat achievement snapshot", PostKind.CA_SNAPSHOT, "tasks", tasks, at);
	}

	public Snapshot clogSnapshot(String discordName, String account, long accountHash, List<OwnedItem> items, Instant at)
	{
		return snapshot(discordName, account, accountHash, "Collection log snapshot", PostKind.CLOG_SNAPSHOT, "items", items, at);
	}

	public Snapshot pbSnapshot(String discordName, String account, long accountHash, List<StoredPersonalBest> bests, Instant at)
	{
		return snapshot(discordName, account, accountHash, "Personal best snapshot", PostKind.PB_SNAPSHOT, "bests", bests, at);
	}

	// Discord previews text attachments inline; the spoiler prefix hides the JSON behind a click
	private static String attachmentName(PostKind kind)
	{
		return "SPOILER_" + kind.getName() + ".json";
	}

	/**
	 * The party is the clan members who fought, so the bot can give every one of them their leaderboard row at once
	 * (decided 2026-10-22); only all-clan teams reach this point, so no outsider's name leaves the client.
	 */
	public WebhookPayload personalBest(String discordName, String account, String boss, String time, TimedLine.Kind kind, int teamSize,
		List<String> party, PersonalBestClaim claim, boolean withScreenshot, Instant at)
	{
		List<WebhookPayload.Field> fields = accountField(account);
		fields.add(new WebhookPayload.Field("Boss", boss, true));
		fields.add(new WebhookPayload.Field("Time", time, true));
		fields.add(new WebhookPayload.Field("Time type", timeType(kind), true));
		fields.add(new WebhookPayload.Field("Team size", String.valueOf(teamSize), true));
		fields.add(new WebhookPayload.Field("Party", String.join(", ", party), false));
		fields.add(new WebhookPayload.Field("New", claim.isNewRecord() ? "yes" : "no", true));
		return payload(discordName, claim.getDescription(), fields, PostKind.PB, at, withScreenshot);
	}

	/**
	 * The game keeps one personal best per boss across team sizes (raids are per size), so a current best reported
	 * at a later kill may come from another scale than today's party. No team size or party is claimed for it.
	 */
	public WebhookPayload currentBossBest(String discordName, String account, String boss, String time, TimedLine.Kind kind, Instant at)
	{
		List<WebhookPayload.Field> fields = accountField(account);
		fields.add(new WebhookPayload.Field("Boss", boss, true));
		fields.add(new WebhookPayload.Field("Time", time, true));
		fields.add(new WebhookPayload.Field("Time type", timeType(kind), true));
		fields.add(new WebhookPayload.Field("Scale", "unknown, the game keeps one personal best per boss", false));
		fields.add(new WebhookPayload.Field("New", "no", true));
		return payload(discordName, "Current personal best", fields, PostKind.PB, at, false);
	}

	private static String timeType(TimedLine.Kind kind)
	{
		return kind.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The fingerprint covers the entries only, so a snapshot taken later with the same content counts as unchanged.
	 */
	private Snapshot snapshot(String discordName, String account, long accountHash, String description, PostKind kind,
		String entriesKey, List<?> entries, Instant at)
	{
		String filename = attachmentName(kind);
		List<WebhookPayload.Field> fields = accountField(account);
		fields.add(new WebhookPayload.Field("Items", String.valueOf(entries.size()), true));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("account", account);
		body.put("accountHash", String.valueOf(accountHash));
		body.put("at", at.toString());
		String entriesJson = gson.toJson(entries);
		body.put(entriesKey, entries);
		return new Snapshot(payload(discordName, description, fields, kind, at, false), Attachment.json(filename, gson.toJson(body)),
			DeliveredSnapshots.fingerprint(entriesJson));
	}

	private static List<WebhookPayload.Field> accountField(String account)
	{
		List<WebhookPayload.Field> fields = new ArrayList<>();
		fields.add(new WebhookPayload.Field("Account", account, true));
		return fields;
	}

	private static WebhookPayload payload(String title, String description, List<WebhookPayload.Field> fields, PostKind kind, Instant at,
		boolean withScreenshot)
	{
		WebhookPayload.EmbedImage image = withScreenshot
			? new WebhookPayload.EmbedImage("attachment://" + MessageFormatter.SCREENSHOT_FILENAME)
			: null;
		return new WebhookPayload(List.of(new WebhookPayload.Embed(title, description, EMBED_COLOR, at.toString(), image, fields,
			new WebhookPayload.Footer(kind.footer(Nonce.next())))));
	}
}
