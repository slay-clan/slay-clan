package com.slayclan.discord;

import com.slayclan.config.TaskDefinition;
import com.slayclan.drop.ValuableDrop;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds task and drop posts. Field names are the bot's contract.
 */
public class MessageFormatter
{
	public static final String SCREENSHOT_FILENAME = "screenshot.png";

	public PostPayload build(String account, TaskDefinition task, Instant completedAt)
	{
		return build(account, task, List.of(), completedAt);
	}

	/**
	 * A task done in a team names the clan members in it, so staff see who was there. A party of one is the
	 * member themselves and adds nothing.
	 */
	public PostPayload build(String account, TaskDefinition task, List<String> party, Instant completedAt)
	{
		Map<String, String> fields = taskFields(task);
		if (party.size() > 1)
		{
			fields.put("Party", String.join(", ", party));
		}
		return payload(PostKind.TASK, account, fields, completedAt);
	}

	public PostPayload buildItemTask(String account, TaskDefinition task, String itemName, String source, Instant completedAt)
	{
		Map<String, String> fields = taskFields(task);
		fields.put("Item", itemName);
		if (source != null)
		{
			fields.put("Source", source);
		}
		return payload(PostKind.TASK, account, fields, completedAt);
	}

	public PostPayload buildDrop(String account, ValuableDrop drop, String source, Instant completedAt)
	{
		String item = drop.getQuantity() > 1 ? drop.getItemName() + " x" + drop.getQuantity() : drop.getItemName();
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Item", item);
		fields.put("Value in M", millions(drop.getValue()));
		fields.put("Points", String.valueOf(drop.getPoints()));
		fields.put("Source", source);
		return payload(PostKind.DROP, account, fields, completedAt);
	}

	public static PostPayload payload(PostKind kind, String account, Map<String, String> fields, Instant at)
	{
		return new PostPayload(kind.getName(), Nonce.next(), account, null, at.toString(), fields, null, false);
	}

	private static Map<String, String> taskFields(TaskDefinition task)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Task", task.getName());
		fields.put("Tier", task.getTier());
		fields.put("Points", String.valueOf(task.getPoints()));
		return fields;
	}

	private static String millions(long value)
	{
		return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(1_000_000), 0, RoundingMode.HALF_UP)
			.stripTrailingZeros().toPlainString();
	}
}
