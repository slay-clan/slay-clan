package com.slayclan.discord;

import com.slayclan.config.TaskDefinition;
import com.slayclan.drop.ValuableDrop;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds posts with the same fields as the clan bot's own submissions: Task, Tier and Points for tasks, and Item,
 * Value in M and Points for drops. Every post ends with the account it was made on and a routing footer.
 */
public class MessageFormatter
{
	public static final String SCREENSHOT_FILENAME = "screenshot.png";
	private static final int EMBED_COLOR = 0x8B0000;
	private static final String TASK_DESCRIPTION = "Has completed the following clan task";
	private static final String DROP_DESCRIPTION = "Has received a valuable drop";
	private static final String SCREENSHOT_FAILED_NOTE = "\n\nScreenshot failed";

	public WebhookPayload build(String discordName, String account, TaskDefinition task, Instant completedAt, boolean withScreenshot)
	{
		return build(discordName, account, task, List.of(), completedAt, withScreenshot);
	}

	/**
	 * A task done in a team names the clan members in it, so staff see who was there. A party of one is the
	 * member themselves and adds nothing.
	 */
	public WebhookPayload build(String discordName, String account, TaskDefinition task, List<String> party, Instant completedAt,
		boolean withScreenshot)
	{
		List<WebhookPayload.Field> fields = withAccount(taskFields(task), account);
		if (party.size() > 1)
		{
			fields.add(new WebhookPayload.Field("Party", String.join(", ", party), false));
		}
		return payload(discordName, TASK_DESCRIPTION, fields, PostKind.TASK, completedAt, withScreenshot);
	}

	public WebhookPayload buildItemTask(String discordName, String account, TaskDefinition task, String itemName, String source,
		Instant completedAt, boolean withScreenshot)
	{
		List<WebhookPayload.Field> fields = taskFields(task);
		fields.add(new WebhookPayload.Field("Item", itemName, true));
		if (source != null)
		{
			fields.add(new WebhookPayload.Field("Source", source, true));
		}
		return payload(discordName, TASK_DESCRIPTION, withAccount(fields, account), PostKind.TASK, completedAt, withScreenshot);
	}

	public WebhookPayload buildDrop(String discordName, String account, ValuableDrop drop, String source, Instant completedAt,
		boolean withScreenshot)
	{
		String item = drop.getQuantity() > 1 ? drop.getItemName() + " x" + drop.getQuantity() : drop.getItemName();
		List<WebhookPayload.Field> fields = new ArrayList<>();
		fields.add(new WebhookPayload.Field("Item", item, true));
		fields.add(new WebhookPayload.Field("Value in M", millions(drop.getValue()), true));
		fields.add(new WebhookPayload.Field("Points", String.valueOf(drop.getPoints()), true));
		fields.add(new WebhookPayload.Field("Source", source, false));
		return payload(discordName, DROP_DESCRIPTION, withAccount(fields, account), PostKind.DROP, completedAt, withScreenshot);
	}

	private static List<WebhookPayload.Field> taskFields(TaskDefinition task)
	{
		List<WebhookPayload.Field> fields = new ArrayList<>();
		fields.add(new WebhookPayload.Field("Task", task.getName(), true));
		fields.add(new WebhookPayload.Field("Tier", task.getTier(), true));
		fields.add(new WebhookPayload.Field("Points", String.valueOf(task.getPoints()), true));
		return fields;
	}

	private static List<WebhookPayload.Field> withAccount(List<WebhookPayload.Field> fields, String account)
	{
		fields.add(new WebhookPayload.Field("Account", account, true));
		return fields;
	}

	private static String millions(long value)
	{
		return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(1_000_000), 0, RoundingMode.HALF_UP)
			.stripTrailingZeros().toPlainString();
	}

	private static WebhookPayload payload(String title, String description, List<WebhookPayload.Field> fields, PostKind kind,
		Instant completedAt, boolean withScreenshot)
	{
		WebhookPayload.EmbedImage image = withScreenshot
			? new WebhookPayload.EmbedImage("attachment://" + SCREENSHOT_FILENAME)
			: null;
		String text = withScreenshot ? description : description + SCREENSHOT_FAILED_NOTE;
		return new WebhookPayload(List.of(new WebhookPayload.Embed(title, text, EMBED_COLOR, completedAt.toString(), image, fields,
			new WebhookPayload.Footer(kind.footer(Nonce.next())))));
	}
}
