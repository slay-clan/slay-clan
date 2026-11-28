package com.slayclan.drop;

import java.util.List;
import java.util.Optional;
import net.runelite.api.Item;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RewardContainerWatchTest
{
	private static final String COFFIN = "Grand Hallowed Coffin";
	private static final int EMPTY = -1;
	private static final int RING = 24844;
	private static final int COINS = 995;
	private static final int SHARK = 385;

	private final RewardContainerWatch watch = new RewardContainerWatch();

	@Test
	public void itemsThatAppearAfterOpeningAContainerAreItsReward()
	{
		watch.clicked(COFFIN, inventory(item(SHARK, 1), empty()), 100);

		Optional<ContainerReward> reward = watch.inventoryChanged(inventory(item(SHARK, 1), item(RING, 1)), 103);

		assertEquals(Optional.of(new ContainerReward(COFFIN, List.of(new Item(RING, 1)))), reward);
	}

	@Test
	public void inventoryChangesWithoutOpeningAContainerAreNoReward()
	{
		assertEquals(Optional.empty(), watch.inventoryChanged(inventory(item(RING, 1)), 100));
	}

	@Test
	public void eatingAfterOpeningAContainerIsNoReward()
	{
		watch.clicked(COFFIN, inventory(item(SHARK, 1)), 100);

		assertEquals(Optional.empty(), watch.inventoryChanged(inventory(empty()), 101));
	}

	@Test
	public void aGrowingStackCountsOnlyTheAddedAmount()
	{
		watch.clicked(COFFIN, inventory(item(COINS, 1000)), 100);

		Optional<ContainerReward> reward = watch.inventoryChanged(inventory(item(COINS, 51000)), 101);

		assertEquals(List.of(new Item(COINS, 50000)), reward.orElseThrow().getGained());
	}

	@Test
	public void itemsAppearingAfterTheWatchWindowAreNoReward()
	{
		watch.clicked(COFFIN, inventory(empty()), 100);

		assertEquals(Optional.empty(), watch.inventoryChanged(inventory(item(RING, 1)), 100 + RewardContainerWatch.WATCH_TICKS + 1));
	}

	@Test
	public void clickingAnythingElseAfterTheContainerEndsTheWatch()
	{
		watch.clicked(COFFIN, inventory(empty()), 100);

		watch.clickedSomethingElse();

		assertEquals(Optional.empty(), watch.inventoryChanged(inventory(item(RING, 1)), 101));
	}

	@Test
	public void rewardIsReportedOnceAndOnlyLaterAdditionsCountAfterIt()
	{
		watch.clicked(COFFIN, inventory(empty(), empty()), 100);
		watch.inventoryChanged(inventory(item(RING, 1), empty()), 101);

		Optional<ContainerReward> later = watch.inventoryChanged(inventory(item(RING, 1), item(SHARK, 1)), 102);

		assertEquals(List.of(new Item(SHARK, 1)), later.orElseThrow().getGained());
	}

	@Test
	public void movingItemsAroundAfterOpeningAContainerIsNoReward()
	{
		watch.clicked(COFFIN, inventory(item(RING, 1), empty()), 100);

		assertEquals(Optional.empty(), watch.inventoryChanged(inventory(empty(), item(RING, 1)), 101));
	}

	@Test
	public void containerOpenedBeforeAWorldHopGivesNoRewardAfterIt()
	{
		watch.clicked(COFFIN, inventory(empty()), 100);

		watch.clear();

		assertEquals(Optional.empty(), watch.inventoryChanged(inventory(item(RING, 1)), 101));
	}

	private static Item[] inventory(Item... items)
	{
		return items;
	}

	private static Item item(int id, int quantity)
	{
		return new Item(id, quantity);
	}

	private static Item empty()
	{
		return new Item(EMPTY, 0);
	}
}
