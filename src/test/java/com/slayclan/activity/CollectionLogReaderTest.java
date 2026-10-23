package com.slayclan.activity;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CollectionLogReaderTest
{
	@Test
	public void formatsSlotsAndCategories()
	{
		Client client = mock(Client.class);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(141);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX)).thenReturn(1717);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_BOSSES)).thenReturn(60);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_BOSSES_MAX)).thenReturn(600);

		CollectionLogStatus status = new CollectionLogReader(client).read();

		assertEquals("141/1717", status.slotsText());
		assertEquals("60/600, 0/0, 0/0, 0/0, 0/0", status.categoriesText());
	}
}
