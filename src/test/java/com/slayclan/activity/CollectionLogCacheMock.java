package com.slayclan.activity;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import net.runelite.client.game.ItemManager;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class CollectionLogCacheMock
{
	private static final int TAB = 471;
	private static final int TAB_PAGES = 2103;
	private static final int FIRST_PAGE = 476;
	private static final int FIRST_PAGE_ITEMS = 2109;

	private CollectionLogCacheMock()
	{
	}

	/** One log tab whose pages hold the given item ids, page by page. */
	static void logPages(Client client, int[]... pages)
	{
		EnumComposition tabs = mock(EnumComposition.class);
		when(tabs.getIntVals()).thenReturn(new int[]{TAB});
		when(client.getEnum(CollectionLogSlots.COLLECTION_LOG_TABS)).thenReturn(tabs);
		StructComposition tab = mock(StructComposition.class);
		when(tab.getIntValue(CollectionLogSlots.TAB_PAGES_PARAM)).thenReturn(TAB_PAGES);
		when(client.getStructComposition(TAB)).thenReturn(tab);
		int[] pageIds = new int[pages.length];
		for (int i = 0; i < pages.length; i++)
		{
			pageIds[i] = FIRST_PAGE + i;
			StructComposition page = mock(StructComposition.class);
			when(page.getIntValue(CollectionLogSlots.PAGE_ITEMS_PARAM)).thenReturn(FIRST_PAGE_ITEMS + i);
			when(client.getStructComposition(pageIds[i])).thenReturn(page);
			EnumComposition items = mock(EnumComposition.class);
			when(items.getIntVals()).thenReturn(pages[i]);
			when(client.getEnum(FIRST_PAGE_ITEMS + i)).thenReturn(items);
		}
		EnumComposition pageEnum = mock(EnumComposition.class);
		when(pageEnum.getIntVals()).thenReturn(pageIds);
		when(client.getEnum(TAB_PAGES)).thenReturn(pageEnum);
	}

	static void replacement(Client client, int from, int to)
	{
		EnumComposition replacements = mock(EnumComposition.class);
		when(replacements.getKeys()).thenReturn(new int[]{from});
		when(replacements.getIntVals()).thenReturn(new int[]{to});
		when(client.getEnum(CollectionLogSlots.ITEM_REPLACEMENTS)).thenReturn(replacements);
	}

	static void item(ItemManager itemManager, int id, String name)
	{
		item(itemManager, id, name, 1000, null);
	}

	static void item(ItemManager itemManager, int id, String name, int model, short[] recolour)
	{
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn(name);
		when(item.getMembersName()).thenReturn(name);
		when(item.getInventoryModel()).thenReturn(model);
		when(item.getColorToReplaceWith()).thenReturn(recolour);
		when(itemManager.getItemComposition(id)).thenReturn(item);
	}
}
