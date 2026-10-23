package com.slayclan.activity;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ItemNameIndexTest
{
	private final Client client = mock(Client.class);
	private final ItemNameIndex index = new ItemNameIndex(client);

	@Test
	public void findsAnUntradeableItemByNameOnceTheCacheIsIndexed()
	{
		when(client.getItemCount()).thenReturn(3);
		item(0, "Musty smelling note", -1, -1);
		item(1, "Pet snakeling", -1, -1);
		item(2, "Vorki", -1, -1);

		assertFalse(index.idOf("Pet snakeling").isPresent());
		index.onTick();

		assertEquals(1, index.idOf("Pet Snakeling").getAsInt());
		assertEquals(0, index.idOf("Musty smelling note").getAsInt());
	}

	@Test
	public void skipsNotedAndPlaceholderVariantsAndKeepsTheRealItem()
	{
		when(client.getItemCount()).thenReturn(3);
		item(0, "Dragon warhammer", 42, -1);
		item(1, "Dragon warhammer", -1, 99);
		item(2, "Dragon warhammer", -1, -1);

		index.onTick();

		assertEquals(2, index.idOf("Dragon warhammer").getAsInt());
	}

	@Test
	public void indexesASliceOfTheCachePerTick()
	{
		int count = ItemNameIndex.ITEMS_PER_TICK + 1;
		when(client.getItemCount()).thenReturn(count);
		for (int id = 0; id < count; id++)
		{
			item(id, "Item " + id, -1, -1);
		}

		index.onTick();
		assertFalse(index.idOf("Item " + ItemNameIndex.ITEMS_PER_TICK).isPresent());
		index.onTick();
		assertEquals(ItemNameIndex.ITEMS_PER_TICK, index.idOf("Item " + ItemNameIndex.ITEMS_PER_TICK).getAsInt());
	}

	private void item(int id, String name, int note, int placeholderTemplate)
	{
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn(name);
		when(item.getNote()).thenReturn(note);
		when(item.getPlaceholderTemplateId()).thenReturn(placeholderTemplate);
		when(client.getItemDefinition(id)).thenReturn(item);
	}
}
