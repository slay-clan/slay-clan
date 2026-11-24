package com.slayclan.activity;

import static com.slayclan.activity.CollectionLogCacheMock.item;
import static com.slayclan.activity.CollectionLogCacheMock.logPages;
import java.util.List;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
		gameCounts(1);
		itemManager = mock(ItemManager.class);
		item(itemManager, 21992, "Vorki");
		capture = new CollectionLogCapture(client, itemManager, () -> captureWanted);
		capture.onLogin();
	}

	@Test
	public void requestsTheFullLogWhenThePlayerOpensTheirOwnLog()
	{
		openLog();

		verify(client).menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
		verify(client).runScript(CollectionLogCapture.COLLECTION_INIT);
	}

	@Test
	public void handsOverTheOwnedItemsTwoTicksAfterTheLastItemArrives()
	{
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));

		assertFalse(capture.onTick(101).isPresent());
		assertEquals(Optional.of(List.of(new OwnedItem(21992, "Vorki", 1))), capture.onTick(102).map(CapturedLog::getItems));
		assertFalse(capture.onTick(103).isPresent());
	}

	@Test
	public void countsAnItemShownOnSeveralPagesOnce()
	{
		gameCounts(2);
		item(itemManager, 20663, "Rocky");
		openLog();
		capture.onScriptPreFired(transmit(20663, 1));
		capture.onScriptPreFired(transmit(21992, 1));
		capture.onScriptPreFired(transmit(20663, 1));

		assertEquals(List.of(new OwnedItem(20663, "Rocky", 1), new OwnedItem(21992, "Vorki", 1)), captured().getItems());
	}

	@Test
	public void countsTheProspectorKitFromBothMiningPagesAsOneItemUnderItsMotherlodeId()
	{
		logPages(client, new int[]{12013}, new int[]{29472});
		item(itemManager, 12013, "Prospector helmet");
		item(itemManager, 29472, "Prospector helmet");
		openLog();
		capture.onScriptPreFired(transmit(29472, 1));
		capture.onScriptPreFired(transmit(12013, 1));

		CapturedLog log = captured();

		assertEquals(List.of(new OwnedItem(12013, "Prospector helmet", 1)), log.getItems());
		assertTrue(log.isComplete());
	}

	@Test
	public void keepsAnItemOnNoLogPageAndMarksTheCaptureAsNotMatchingTheGame()
	{
		logPages(client, new int[]{21992});
		item(itemManager, 995, "Coins");
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));
		capture.onScriptPreFired(transmit(995, 1));

		CapturedLog log = captured();

		assertEquals(List.of(new OwnedItem(21992, "Vorki", 1), new OwnedItem(995, "Coins", 1)), log.getItems());
		assertFalse(log.isComplete());
	}

	@Test
	public void aCaptureThatFailsWhileSortingIntoSlotsIsDroppedOnceInsteadOfFailingEveryTick()
	{
		logPages(client, new int[]{21992});
		ItemComposition vorki = itemManager.getItemComposition(21992);
		when(itemManager.getItemComposition(21992)).thenReturn(vorki).thenThrow(new IllegalStateException("cache"));
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));

		assertThrows(IllegalStateException.class, () -> capture.onTick(102));
		assertFalse(capture.onTick(103).isPresent());
	}

	@Test
	public void openingsWhereTheLogSentNothingCountTowardsTheCapturesAllowedPerLogin()
	{
		for (int i = 0; i < CollectionLogCapture.MAX_CAPTURES_PER_LOGIN; i++)
		{
			openLog();
			capture.onTick(100 + CollectionLogCapture.NO_ITEMS_TIMEOUT_TICKS + 1);
		}

		openLog();

		verify(client, times(CollectionLogCapture.MAX_CAPTURES_PER_LOGIN)).runScript(anyInt());
	}

	@Test
	public void namesItemsWithoutTheMembersSuffixOnFreeWorlds()
	{
		ItemComposition vorki = mock(ItemComposition.class);
		when(vorki.getName()).thenReturn("Vorki (Members)");
		when(vorki.getMembersName()).thenReturn("Vorki");
		when(itemManager.getItemComposition(21992)).thenReturn(vorki);
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));

		assertEquals("Vorki", captured().getItems().get(0).getName());
	}

	@Test
	public void marksTheCaptureCompleteWhenItHoldsAsManyItemsAsTheGameCounts()
	{
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));

		CapturedLog log = captured();

		assertEquals(1, log.getGameCount());
		assertTrue(log.isComplete());
	}

	@Test
	public void marksTheCaptureIncompleteAndCapturesAgainAtTheNextOpening()
	{
		gameCounts(2);
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));
		assertFalse(captured().isComplete());

		openLog();

		verify(client, times(2)).runScript(anyInt());
	}

	@Test
	public void stopsCapturingAgainAfterThreeIncompleteCapturesInOneLogin()
	{
		gameCounts(2);
		for (int i = 0; i < CollectionLogCapture.MAX_CAPTURES_PER_LOGIN; i++)
		{
			openLog();
			capture.onScriptPreFired(transmit(21992, 1));
			captured();
		}

		openLog();

		verify(client, times(CollectionLogCapture.MAX_CAPTURES_PER_LOGIN)).runScript(anyInt());
	}

	@Test
	public void capturesOnlyOncePerLoginWhenTheCaptureIsComplete()
	{
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));
		captured();

		openLog();

		verify(client, times(1)).runScript(anyInt());
	}

	@Test
	public void capturesAgainAfterANewLogin()
	{
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));
		captured();
		capture.onLogin();

		openLog();

		verify(client, times(2)).runScript(anyInt());
	}

	@Test
	public void ignoresSomeoneElsesLogInThePoh()
	{
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);

		openLog();
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

		openLog();

		verify(client, never()).runScript(anyInt());
	}

	@Test
	public void capturesAtTheNextOpeningWhenTheCapturedListCouldNotBeUsed()
	{
		openLog();
		capture.onScriptPreFired(transmit(21992, 1));
		captured();
		capture.allowAgain();

		openLog();

		verify(client, times(2)).runScript(anyInt());
	}

	@Test
	public void givesUpACaptureThatGetsNoItemsAndLetsTheNextOpeningTryAgain()
	{
		openLog();
		int givenUp = 100 + CollectionLogCapture.NO_ITEMS_TIMEOUT_TICKS + 1;
		assertFalse(capture.onTick(givenUp).isPresent());

		capture.onScriptPreFired(transmit(21992, 1));
		assertFalse(capture.onTick(givenUp + 2).isPresent());

		openLog();
		verify(client, times(2)).runScript(anyInt());
	}

	private void gameCounts(int obtained)
	{
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(obtained);
	}

	private void openLog()
	{
		capture.onScriptPostFired(new ScriptPostFired(CollectionLogCapture.COLLECTION_LOG_SETUP));
	}

	private CapturedLog captured()
	{
		return capture.onTick(102).orElseThrow();
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
