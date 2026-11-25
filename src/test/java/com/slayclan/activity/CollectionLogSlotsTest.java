package com.slayclan.activity;

import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CollectionLogSlotsTest
{
	private Client client;
	private ItemManager itemManager;
	private CollectionLogCacheMock cache;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		cache = new CollectionLogCacheMock(client, itemManager);
	}

	@Test
	public void countsTheProspectorKitListedOnTwoPagesUnderTwoIdsAsOneSlotUnderTheLowestId()
	{
		cache.logPages(new int[]{12013}, new int[]{29472});
		cache.item(12013, "Prospector helmet");
		cache.item(29472, "Prospector helmet");

		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertEquals(Integer.valueOf(12013), slots.slotOf(29472));
		assertEquals(Integer.valueOf(12013), slots.slotOf(12013));
		assertEquals(1, slots.count());
	}

	@Test
	public void keepsGracefulPiecesFromDifferentCoursesApartSinceTheirColoursDiffer()
	{
		cache.logPages(new int[]{11850}, new int[]{21061});
		cache.item(11850, "Graceful hood", 1000, new short[]{1});
		cache.item(21061, "Graceful hood", 1000, new short[]{2});

		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertEquals(Integer.valueOf(21061), slots.slotOf(21061));
		assertEquals(2, slots.count());
	}

	@Test
	public void keepsSameNameItemsWithADifferentModelApart()
	{
		cache.logPages(new int[]{100}, new int[]{200});
		cache.item(100, "Medallion fragment", 1000, null);
		cache.item(200, "Medallion fragment", 2000, null);

		assertEquals(2, CollectionLogSlots.read(client, itemManager).count());
	}

	@Test
	public void fallsBackToOneSlotPerItemWhenAPageOfTheLogIsMissingFromTheCache()
	{
		cache.logPages(new int[]{21992});
		when(client.getEnum(CollectionLogCacheMock.FIRST_PAGE_ITEMS)).thenReturn(null);

		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertFalse(slots.isKnown());
		assertEquals(Integer.valueOf(21992), slots.slotOf(21992));
	}

	@Test
	public void keepsLookAlikeItemsOnTheSamePageApart()
	{
		cache.logPages(new int[]{24763, 24765});
		cache.item(24763, "Mysterious page");
		cache.item(24765, "Mysterious page");

		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertEquals(Integer.valueOf(24765), slots.slotOf(24765));
		assertEquals(2, slots.count());
	}

	@Test
	public void keepsALookAlikeApartWhenItSharesAPageWithAnyItemOfTheSlot()
	{
		cache.logPages(new int[]{100, 300}, new int[]{200});
		cache.item(100, "Rum");
		cache.item(200, "Rum");
		cache.item(300, "Rum");

		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertEquals(Integer.valueOf(100), slots.slotOf(200));
		assertEquals(Integer.valueOf(300), slots.slotOf(300));
		assertEquals(2, slots.count());
	}

	@Test
	public void countsAnItemShownOnSeveralPagesUnderOneIdOnce()
	{
		cache.logPages(new int[]{4151}, new int[]{4151});
		cache.item(4151, "Abyssal whip");

		assertEquals(1, CollectionLogSlots.read(client, itemManager).count());
	}

	@Test
	public void countsAnItemUnderTheIdTheLogShowsForIt()
	{
		cache.replacement(10859, 25617);
		cache.logPages(new int[]{10859});
		cache.item(25617, "Tea flask");

		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertEquals(Integer.valueOf(25617), slots.slotOf(10859));
		assertEquals(Integer.valueOf(25617), slots.slotOf(25617));
	}

	@Test
	public void findsNoSlotForAnItemThatIsNotInTheLog()
	{
		cache.logPages(new int[]{21992});
		cache.item(21992, "Vorki");

		assertNull(CollectionLogSlots.read(client, itemManager).slotOf(995));
	}

	@Test
	public void treatsEveryItemAsItsOwnSlotWhenTheCacheHasNoLog()
	{
		CollectionLogSlots slots = CollectionLogSlots.read(client, itemManager);

		assertFalse(slots.isKnown());
		assertEquals(Integer.valueOf(21992), slots.slotOf(21992));
	}
}
