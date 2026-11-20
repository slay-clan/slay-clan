package com.slayclan.task;

import com.slayclan.activity.OwnedItem;
import com.slayclan.config.TaskDefinition;
import com.slayclan.discord.ProofBox;
import com.slayclan.trigger.Triggers;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;

/**
 * Judges tasks on what the member holds and has reached right now, and says what completed a task judged on
 * the game's records, for the box drawn on its screenshot. Must be called on the client thread: RuneLite then
 * fills an item's icon in at once, so it is ready when the box is drawn on another thread.
 */
public class TaskProofs
{
	private final Client client;
	private final ItemManager itemManager;
	private final ThresholdTaskEvaluator thresholds = new ThresholdTaskEvaluator();

	public TaskProofs(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	public ProofBox.Proof listedItems(TaskDefinition task, List<OwnedItem> owned)
	{
		List<OwnedItem> listed = owned.stream()
			.filter(item -> Names.matchesAny(task.getItems(), item.getName()))
			.collect(Collectors.toList());
		return ProofBox.Proof.of(listed, item -> new ProofBox.Entry(item.getName(), itemManager.getImage(item.getId())));
	}

	/**
	 * The still open threshold tasks the member meets right now, by task id, each with what meets it. A task whose
	 * value the game has not loaded yet is left out, like one that is not met. This runs on every experience drop,
	 * so a task is only asked whether it is open once it is met, and its proof is only built when it is both.
	 */
	public Map<String, ProofBox.Proof> thresholdsReached(List<TaskDefinition> tasks, Predicate<TaskDefinition> stillOpen)
	{
		List<OwnedItem> worn = items(client.getItemContainer(InventoryID.WORN));
		List<OwnedItem> held = Stream.concat(worn.stream(), items(client.getItemContainer(InventoryID.INV)).stream())
			.distinct()
			.collect(Collectors.toList());
		Map<String, ProofBox.Proof> reached = new LinkedHashMap<>();
		for (TaskDefinition task : tasks)
		{
			reachedBy(task, worn, held)
				.filter(proof -> stillOpen.test(task))
				.ifPresent(proof -> reached.put(task.getId(), proof.get()));
		}
		return reached;
	}

	public Set<String> itemNames(ItemContainer container)
	{
		return names(items(container));
	}

	private Optional<Supplier<ProofBox.Proof>> reachedBy(TaskDefinition task, List<OwnedItem> worn, List<OwnedItem> held)
	{
		String trigger = task.getTrigger();
		if (trigger == null)
		{
			return Optional.empty();
		}
		if (Names.same(trigger, Triggers.EQUIP))
		{
			return having(task, worn);
		}
		if (Names.same(trigger, Triggers.HOLD))
		{
			return having(task, held);
		}
		if (Names.same(trigger, Triggers.CLOG_COUNT))
		{
			return Optional.of(client.getVarpValue(VarPlayerID.COLLECTION_COUNT))
				.filter(slots -> loadedAndEnoughSlots(task, slots))
				.map(slots -> () -> ProofBox.Proof.text(slots + " collection log slots"));
		}
		return ThresholdTaskEvaluator.xpSkill(trigger).flatMap(skill -> trainedTo(task, skill));
	}

	private Optional<Supplier<ProofBox.Proof>> having(TaskDefinition task, List<OwnedItem> items)
	{
		return thresholds.isEquipped(task, names(items)) ? Optional.of(() -> listedItems(task, items)) : Optional.empty();
	}

	private Optional<Supplier<ProofBox.Proof>> trainedTo(TaskDefinition task, Skill skill)
	{
		return Optional.of(client.getSkillExperience(skill))
			.filter(experience -> loadedAndEnoughExperience(task, experience))
			.map(experience -> () -> ProofBox.Proof.text(String.format(Locale.ENGLISH, "%s: %,d xp", skill.getName(), experience)));
	}

	// The game reports 0 until it has loaded the value
	private boolean loadedAndEnoughSlots(TaskDefinition task, int slots)
	{
		return slots > 0 && thresholds.hasCount(task, slots);
	}

	private boolean loadedAndEnoughExperience(TaskDefinition task, int experience)
	{
		return experience > 0 && thresholds.hasExperience(task, experience);
	}

	private List<OwnedItem> items(ItemContainer container)
	{
		if (container == null)
		{
			return List.of();
		}
		return Arrays.stream(container.getItems())
			.filter(item -> item.getId() > 0)
			.map(item -> new OwnedItem(item.getId(), itemManager.getItemComposition(item.getId()).getName(), 1))
			.distinct()
			.collect(Collectors.toList());
	}

	private static Set<String> names(List<OwnedItem> items)
	{
		return items.stream().map(OwnedItem::getName).collect(Collectors.toSet());
	}
}
