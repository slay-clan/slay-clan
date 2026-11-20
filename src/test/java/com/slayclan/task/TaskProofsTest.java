package com.slayclan.task;

import com.slayclan.activity.OwnedItem;
import com.slayclan.config.TaskDefinition;
import com.slayclan.discord.ProofStrip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskProofsTest
{
	private Client client;
	private ItemManager itemManager;
	private TaskProofs proofs;
	private int nextItemId = 1000;
	private final Map<String, Integer> idsByName = new HashMap<>();

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		proofs = new TaskProofs(client, itemManager);
	}

	@Test
	public void aCollectionLogTaskIsProvenByTheListedItemsTheMemberOwnsWithTheirIcons()
	{
		AsyncBufferedImage whipIcon = new AsyncBufferedImage(mock(ClientThread.class), 36, 32, AsyncBufferedImage.TYPE_INT_ARGB);
		when(itemManager.getImage(4151)).thenReturn(whipIcon);
		TaskDefinition task = task("CLOG", "Abyssal whip", "Pharaoh's*");

		ProofStrip.Proof proof = proofs.listedItems(task, List.of(
			new OwnedItem(4151, "Abyssal whip", 1), new OwnedItem(995, "Coins", 100), new OwnedItem(26945, "Pharaohs sceptre", 1)));

		assertEquals(List.of("Abyssal whip", "Pharaohs sceptre"), labels(proof));
		assertSame(whipIcon, proof.getShown().get(0).getIcon());
	}

	@Test
	public void aTaskCompletedByMoreItemsThanTheStripShowsLoadsIconsOnlyForTheOnesShown()
	{
		List<OwnedItem> owned = new ArrayList<>();
		for (int i = 0; i < 12; i++)
		{
			owned.add(new OwnedItem(i + 1, "Rune pouch " + i, 1));
		}

		ProofStrip.Proof proof = proofs.listedItems(task("CLOG", "Rune pouch*"), owned);

		assertEquals(8, proof.getShown().size());
		assertEquals(4, proof.getLeftOut());
		verify(itemManager, times(8)).getImage(anyInt());
	}

	@Test
	public void anEquipmentTaskIsReachedByWearingAListedItemAndProvenByIt()
	{
		holding(InventoryID.WORN, "Quest point cape", "Dragon boots");
		holding(InventoryID.INV, "Fire cape");

		assertEquals(List.of("Quest point cape"), labels(reached(task("EQUIP", "Quest point cape", "Fire cape"))));
	}

	@Test
	public void anEquipmentTaskIsNotReachedByAListedItemThatIsOnlyCarried()
	{
		holding(InventoryID.WORN, "Dragon boots");
		holding(InventoryID.INV, "Quest point cape");

		assertTrue(proofs.thresholdsReached(List.of(task("EQUIP", "Quest point cape")), open -> true).isEmpty());
	}

	@Test
	public void aTaskAboutHeldItemsIsProvenByTheListedItemsWornOrCarriedEachNamedOnce()
	{
		holding(InventoryID.WORN, "Quest point cape");
		holding(InventoryID.INV, "Fire cape", "Fire cape");

		assertEquals(List.of("Quest point cape", "Fire cape"), labels(reached(task("HOLD", "Quest point cape", "Fire cape"))));
	}

	@Test
	public void aCollectionLogCountTaskIsReachedAtItsCountAndProvenByTheSlotsFilled()
	{
		TaskDefinition task = task("CLOG_COUNT");
		task.setMinCount(500);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(499, 512);

		assertTrue(proofs.thresholdsReached(List.of(task), open -> true).isEmpty());
		assertEquals(List.of("512 collection log slots"), labels(reached(task)));
	}

	@Test
	public void anExperienceTaskIsReachedAtItsExperienceAndProvenByTheSkillAndItsExperience()
	{
		TaskDefinition task = task("XP:Agility");
		task.setMinXp(13_034_431);
		when(client.getSkillExperience(Skill.AGILITY)).thenReturn(13_000_000, 13_034_431);

		assertTrue(proofs.thresholdsReached(List.of(task), open -> true).isEmpty());
		assertEquals(List.of("Agility: 13,034,431 xp"), labels(reached(task)));
	}

	@Test
	public void aThresholdTaskAlreadyPostedIsLeftOutWithoutLoadingAnyIcon()
	{
		holding(InventoryID.WORN, "Quest point cape");

		assertTrue(proofs.thresholdsReached(List.of(task("EQUIP", "Quest point cape")), alreadyPosted -> false).isEmpty());
		verify(itemManager, never()).getImage(anyInt());
	}

	@Test
	public void aThresholdTheGameHasNotLoadedYetCountsAsNotReached()
	{
		assertTrue(proofs.thresholdsReached(List.of(task("CLOG_COUNT"), task("XP:Agility")), open -> true).isEmpty());
	}

	@Test
	public void aTaskThatIsNotAboutAThresholdIsNeverReachedThisWay()
	{
		holding(InventoryID.WORN, "Quest point cape");

		assertTrue(proofs.thresholdsReached(List.of(task("KILL:Zulrah", "Quest point cape"), task(null)), open -> true).isEmpty());
	}

	@Test
	public void theItemsInAContainerAreNamedOnceEachAndAnEmptySlotIsSkipped()
	{
		ItemContainer container = holding(InventoryID.INV, "Fire cape", "Fire cape", "Coins");
		Item[] withEmptySlot = new Item[]{container.getItems()[0], new Item(-1, 0), container.getItems()[2]};
		when(container.getItems()).thenReturn(withEmptySlot);

		assertEquals(Set.of("Fire cape", "Coins"), proofs.itemNames(container));
		assertEquals(Set.of(), proofs.itemNames(null));
	}

	private ProofStrip.Proof reached(TaskDefinition task)
	{
		Map<String, ProofStrip.Proof> reached = proofs.thresholdsReached(List.of(task), open -> true);
		assertEquals(Set.of(task.getId()), reached.keySet());
		return reached.get(task.getId());
	}

	private ItemContainer holding(int containerId, String... itemNames)
	{
		ItemContainer container = mock(ItemContainer.class);
		Item[] items = new Item[itemNames.length];
		for (int i = 0; i < itemNames.length; i++)
		{
			int id = idOf(itemNames[i]);
			items[i] = new Item(id, 1);
		}
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(containerId)).thenReturn(container);
		return container;
	}

	private int idOf(String itemName)
	{
		return idsByName.computeIfAbsent(itemName, name ->
		{
			int id = nextItemId++;
			ItemComposition composition = mock(ItemComposition.class);
			when(composition.getName()).thenReturn(name);
			when(itemManager.getItemComposition(id)).thenReturn(composition);
			return id;
		});
	}

	private static TaskDefinition task(String trigger, String... items)
	{
		TaskDefinition task = new TaskDefinition();
		task.setId("task");
		task.setTrigger(trigger);
		task.setItems(List.of(items));
		return task;
	}

	private static List<String> labels(ProofStrip.Proof proof)
	{
		return proof.getShown().stream().map(ProofStrip.Entry::getLabel).collect(Collectors.toList());
	}
}
