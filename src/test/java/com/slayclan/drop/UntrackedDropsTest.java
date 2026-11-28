package com.slayclan.drop;

import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class UntrackedDropsTest
{
	private static final NamedItem RING_OF_ENDURANCE = new NamedItem("Ring of endurance (uncharged)", 24844, 1);
	private static final NamedItem DRAGON_PICKAXE = new NamedItem("Dragon pickaxe", 11920, 1);
	private static final String COFFIN = "Grand Hallowed Coffin";
	private static final int LAST_TICK_OF_THE_WAIT = 100 + UntrackedDrops.COLLECTION_LOG_WAIT_TICKS;

	private final UntrackedDrops drops = new UntrackedDrops();

	@Test
	public void newCollectionLogItemWithoutALootReportIsPostedFromTheCollectionLogOnceTheWaitIsOver()
	{
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);

		assertEquals(List.of(), drops.due(LAST_TICK_OF_THE_WAIT));
		assertEquals(List.of(new UntrackedLoot(UntrackedDrops.COLLECTION_LOG_SOURCE, List.of(RING_OF_ENDURANCE))),
			drops.due(LAST_TICK_OF_THE_WAIT + 1));
	}

	@Test
	public void lootTrackerReportOnTheLastTickOfTheWaitStillMeansNoSecondPost()
	{
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);

		assertEquals(List.of(), drops.due(LAST_TICK_OF_THE_WAIT));
		drops.lootTrackerReported(List.of("Ring of endurance (uncharged)"), LAST_TICK_OF_THE_WAIT);

		assertEquals(List.of(), drops.due(200));
	}

	@Test
	public void newCollectionLogItemIsPostedOnlyOnceAlthoughTicksKeepPassing()
	{
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);

		drops.due(LAST_TICK_OF_THE_WAIT + 1);

		assertEquals(List.of(), drops.due(200));
	}

	@Test
	public void lootTrackerReportingTheItemAfterTheCollectionLogLineMeansNoSecondPost()
	{
		drops.collectionLogItem(DRAGON_PICKAXE, 100);
		drops.lootTrackerReported(List.of("Dragon pickaxe"), LAST_TICK_OF_THE_WAIT - 1);

		assertEquals(List.of(), drops.due(200));
	}

	@Test
	public void lootTrackerReportingTheItemJustBeforeTheCollectionLogLineMeansNoSecondPost()
	{
		drops.lootTrackerReported(List.of("dragon pickaxe"), 100);
		drops.collectionLogItem(DRAGON_PICKAXE, 102);

		assertEquals(List.of(), drops.due(200));
	}

	@Test
	public void lootTrackerReportLongBeforeTheCollectionLogLineStillMeansNoSecondPost()
	{
		drops.lootTrackerReported(List.of("Dragon pickaxe"), 100);
		drops.collectionLogItem(DRAGON_PICKAXE, 1000);

		assertEquals(List.of(), drops.due(2000));
	}

	@Test
	public void lootTrackerReportBeforeAWorldHopNoLongerCoversANewCollectionLogItemAfterIt()
	{
		drops.lootTrackerReported(List.of("Dragon pickaxe"), 100);
		drops.clear();
		drops.collectionLogItem(DRAGON_PICKAXE, 1000);

		assertEquals(1, drops.due(2000).size());
	}

	@Test
	public void lootTrackerReportOfAnotherItemLeavesTheNewCollectionLogItemToBePosted()
	{
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);
		drops.lootTrackerReported(List.of("Dragon pickaxe"), 101);

		assertEquals(1, drops.due(200).size());
	}

	@Test
	public void rewardFromAContainerIsPostedWithTheContainerAsSourceAfterAShortWait()
	{
		coffinGave(RING_OF_ENDURANCE, 100);

		assertEquals(List.of(), drops.due(100 + UntrackedDrops.CONTAINER_WAIT_TICKS));
		assertEquals(List.of(new UntrackedLoot(COFFIN, List.of(RING_OF_ENDURANCE))), drops.due(100 + UntrackedDrops.CONTAINER_WAIT_TICKS + 1));
	}

	@Test
	public void sameItemFromTheSameContainerLaterInTheSessionIsPostedAgain()
	{
		coffinGave(RING_OF_ENDURANCE, 100);
		drops.due(200);

		coffinGave(RING_OF_ENDURANCE, 1000);

		assertEquals(1, drops.due(1100).size());
	}

	@Test
	public void containerRewardTheLootTrackerAlsoReportsIsLeftToTheLootTracker()
	{
		coffinGave(RING_OF_ENDURANCE, 100);
		drops.lootTrackerReported(List.of("Ring of endurance (uncharged)"), 100);

		assertEquals(List.of(), drops.due(200));
	}

	@Test
	public void containerRewardTheLootTrackerReportedATickEarlierIsLeftToTheLootTracker()
	{
		drops.lootTrackerReported(List.of("Ring of endurance (uncharged)"), 99);
		coffinGave(RING_OF_ENDURANCE, 100);

		assertEquals(List.of(), drops.due(200));
	}

	@Test
	public void containerRewardAndItsCollectionLogLineArePostedOnceUnderTheContainer()
	{
		coffinGave(RING_OF_ENDURANCE, 100);
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);

		assertEquals(List.of(new UntrackedLoot(COFFIN, List.of(RING_OF_ENDURANCE))), drops.due(200));
		assertEquals(List.of(), drops.due(300));
	}

	@Test
	public void collectionLogLineBeforeTheContainerRewardArrivesIsPostedOnceUnderTheContainer()
	{
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);
		coffinGave(RING_OF_ENDURANCE, 101);

		assertEquals(List.of(new UntrackedLoot(COFFIN, List.of(RING_OF_ENDURANCE))), drops.due(200));
	}

	@Test
	public void collectionLogLineLongAfterTheContainerRewardWasPostedIsNotPostedAgain()
	{
		coffinGave(RING_OF_ENDURANCE, 100);
		assertEquals(1, drops.due(200).size());

		drops.collectionLogItem(RING_OF_ENDURANCE, 1000);

		assertEquals(List.of(), drops.due(2000));
	}

	@Test
	public void nothingThatWasWaitingIsPostedAfterAWorldHop()
	{
		drops.collectionLogItem(RING_OF_ENDURANCE, 100);
		coffinGave(DRAGON_PICKAXE, 100);

		drops.clear();

		assertEquals(List.of(), drops.due(300));
	}

	private void coffinGave(NamedItem item, int tick)
	{
		drops.containerGave(new UntrackedLoot(COFFIN, List.of(item)), tick);
	}
}
