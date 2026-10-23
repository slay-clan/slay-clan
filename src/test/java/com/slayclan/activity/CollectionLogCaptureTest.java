package com.slayclan.activity;

import java.util.List;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectionLogCaptureTest
{
	private Client client;
	private ItemManager itemManager;
	private boolean captureWanted = true;
	private CollectionLogCapture capture;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		when(client.getTickCount()).thenReturn(100);
		itemManager = mock(ItemManager.class);
		ItemComposition vorki = mock(ItemComposition.class);
		when(vorki.getName()).thenReturn("Vorki");
		when(itemManager.getItemComposition(21992)).thenReturn(vorki);
		capture = new CollectionLogCapture(client, itemManager, () -> captureWanted);
		capture.onLogin();
	}

	@Test
	public void requestsTheFullLogWhenThePlayerOpensTheirOwnLog()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));

		verify(client).menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
		verify(client).runScript(CollectionLogCapture.COLLECTION_INIT);
	}

	@Test
	public void collectsItemsAndHandsThemOverTwoTicksAfterTheLast()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(21992, 1));

		assertFalse(capture.onTick(101).isPresent());
		assertEquals(Optional.of(List.of(new OwnedItem(21992, "Vorki", 1))), capture.onTick(102));
		assertFalse(capture.onTick(103).isPresent());
	}

	@Test
	public void foldsItemsSentOnSeveralPagesIntoOne()
	{
		ItemComposition rocky = mock(ItemComposition.class);
		when(rocky.getName()).thenReturn("Rocky");
		when(itemManager.getItemComposition(20663)).thenReturn(rocky);
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(20663, 1));
		capture.onScriptPreFired(transmit(21992, 1));
		capture.onScriptPreFired(transmit(20663, 1));

		assertEquals(Optional.of(List.of(new OwnedItem(20663, "Rocky", 1), new OwnedItem(21992, "Vorki", 1))), capture.onTick(102));
	}

	@Test
	public void mapsReplacedItemIdsOntoTheirReplacement()
	{
		EnumComposition replacements = mock(EnumComposition.class);
		when(replacements.getKeys()).thenReturn(new int[]{12013});
		when(replacements.getIntVals()).thenReturn(new int[]{29472});
		when(client.getEnum(CollectionLogCapture.ITEM_REPLACEMENTS)).thenReturn(replacements);
		ItemComposition helmet = mock(ItemComposition.class);
		when(helmet.getName()).thenReturn("Prospector helmet");
		when(itemManager.getItemComposition(12013)).thenReturn(helmet);
		when(itemManager.getItemComposition(29472)).thenReturn(helmet);
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(12013, 1));
		capture.onScriptPreFired(transmit(29472, 1));

		assertEquals(Optional.of(List.of(new OwnedItem(29472, "Prospector helmet", 1))), capture.onTick(102));
	}

	@Test
	public void foldsAnItemSentUnderTwoIdsWithTheSameNameOntoTheLowerId()
	{
		ItemComposition helmet = mock(ItemComposition.class);
		when(helmet.getName()).thenReturn("Prospector helmet");
		when(itemManager.getItemComposition(12013)).thenReturn(helmet);
		when(itemManager.getItemComposition(29472)).thenReturn(helmet);
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(29472, 1));
		capture.onScriptPreFired(transmit(12013, 2));

		assertEquals(Optional.of(List.of(new OwnedItem(12013, "Prospector helmet", 2))), capture.onTick(102));
	}

	@Test
	public void capturesOnlyOncePerLogin()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(21992, 1));
		capture.onTick(102);

		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));

		verify(client, times(1)).runScript(anyInt());
	}

	@Test
	public void capturesAgainAfterANewLogin()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(21992, 1));
		capture.onTick(102);
		capture.onLogin();

		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));

		verify(client, times(2)).runScript(anyInt());
	}

	@Test
	public void ignoresSomeoneElsesLogInThePoh()
	{
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);

		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(21992, 1));

		verify(client, never()).runScript(anyInt());
		assertFalse(capture.onTick(103).isPresent());
	}

	@Test
	public void ignoresLogItemsSentWhileNoCaptureIsRunning()
	{
		capture.onScriptPreFired(transmit(21992, 1));

		assertFalse(capture.onTick(103).isPresent());
	}

	@Test
	public void leavesTheLogAloneWhenNobodyWantsTheCapture()
	{
		captureWanted = false;

		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));

		verify(client, never()).runScript(anyInt());
	}

	@Test
	public void capturesAtTheNextOpeningWhenTheCapturedListCouldNotBeUsed()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		capture.onScriptPreFired(transmit(21992, 1));
		capture.onTick(102);
		capture.allowAgain();

		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));

		verify(client, times(2)).runScript(anyInt());
	}

	@Test
	public void givesUpACaptureThatGetsNoItemsAndLetsTheNextOpeningTryAgain()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		int givenUp = 100 + CollectionLogCapture.NO_ITEMS_TIMEOUT_TICKS + 1;
		assertFalse(capture.onTick(givenUp).isPresent());

		capture.onScriptPreFired(transmit(21992, 1));
		assertFalse(capture.onTick(givenUp + 2).isPresent());

		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
		verify(client, times(2)).runScript(anyInt());
	}

	private static ScriptPreFired transmit(int itemId, int quantity)
	{
		ScriptEvent event = mock(ScriptEvent.class);
		when(event.getArguments()).thenReturn(new Object[]{0, itemId, quantity});
		ScriptPreFired fired = new ScriptPreFired(CollectionLogCapture.COLLECTION_TRANSMIT);
		fired.setScriptEvent(event);
		return fired;
	}
}
