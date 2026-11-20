package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScreenshotServiceTest
{
	private static final int PUBLIC_TAB = 2;
	private static final int SPLIT_PRIVATE_CHAT = 1;
	private static final int PRIVATE_CHAT_IN_CHATBOX = 0;
	private static final List<Object> REBUILD_CHAT = List.of(ScriptID.SPLITPM_CHANGED);

	@Mock
	private Client client;
	@Mock
	private ClientThread clientThread;
	@Mock
	private DrawManager drawManager;
	@Mock
	private ScheduledExecutorService executor;
	@Mock
	private SlayClanConfig config;
	@Mock
	private Widget privateMessages;
	@Mock
	private ScheduledFuture<?> timeout;

	private ScreenshotService service;
	private final List<byte[]> captured = new ArrayList<>();
	private final List<List<Object>> scriptsRun = new ArrayList<>();
	private final List<Runnable> timeouts = new ArrayList<>();
	private int chatView = ScreenshotChat.ALL_TAB;
	private int framesDrawn;

	@Before
	public void setUp()
	{
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		lenient().doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		lenient().doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).submit(any(Runnable.class));
		doAnswer(invocation ->
		{
			timeouts.add(invocation.getArgument(0));
			return timeout;
		}).when(executor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		lenient().when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		lenient().when(client.getVarpValue(VarPlayerID.OPTION_PM)).thenReturn(SPLIT_PRIVATE_CHAT);
		lenient().when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(privateMessages);
		lenient().when(client.getVarcIntValue(VarClientID.CHAT_VIEW)).thenAnswer(invocation -> chatView);
		lenient().doAnswer(invocation ->
		{
			List<Object> script = Arrays.asList(invocation.getArguments());
			scriptsRun.add(script);
			clickChatTabLikeTheGame(script);
			return null;
		}).when(client).runScript(any(Object[].class));
		lenient().when(config.hidePrivateMessages()).thenReturn(true);
		lenient().when(config.hidePlayerChat()).thenReturn(true);
		lenient().when(config.showGameChat()).thenReturn(true);
		service = new ScreenshotService(client, clientThread, drawManager, executor, config);
	}

	@Test
	public void privateMessagesAreHiddenWhileTheFrameIsDrawnAndShownAgainAfterwards()
	{
		service.capture(captured::add);
		drawFrame();

		InOrder inOrder = inOrder(privateMessages, drawManager);
		inOrder.verify(privateMessages).setHidden(true);
		inOrder.verify(drawManager).requestNextFrameListener(any());
		inOrder.verify(privateMessages).setHidden(false);
		assertEquals(1, captured.size());
		assertNotNull(captured.get(0));
	}

	@Test
	public void privateMessagesStayVisibleWhenTheSettingIsOff()
	{
		when(config.hidePrivateMessages()).thenReturn(false);

		service.capture(captured::add);
		drawFrame();

		verify(privateMessages, never()).setHidden(true);
		assertEquals(1, captured.size());
	}

	@Test
	public void privateMessagesThePlayerHidThemselvesStayHidden()
	{
		when(privateMessages.isHidden()).thenReturn(true);

		service.capture(captured::add);
		drawFrame();

		verify(privateMessages, never()).setHidden(true);
		verify(privateMessages, never()).setHidden(false);
	}

	@Test
	public void splitPrivateChatTurnedOffDuringTheFrameIsNotShownAgain()
	{
		service.capture(captured::add);
		when(client.getVarpValue(VarPlayerID.OPTION_PM)).thenReturn(PRIVATE_CHAT_IN_CHATBOX);
		drawFrame();

		verify(privateMessages, never()).setHidden(false);
	}

	@Test
	public void theScreenshotIsStillTakenWhenThePrivateMessageInterfaceIsNotLoaded()
	{
		when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(null);

		service.capture(captured::add);
		drawFrame();

		assertEquals(1, captured.size());
		assertNotNull(captured.get(0));
	}

	@Test
	public void aScreenshotTakenForAJudgedTaskArrivesAsAnImageThatCanStillBePostedAsPng()
	{
		List<BufferedImage> frames = new ArrayList<>();

		service.captureFrame(frames::add);
		drawFrame();

		assertEquals(1, frames.get(0).getWidth());
		assertNotNull(ScreenshotService.toPng(frames.get(0)));
	}

	@Test
	public void playerChatIsLeftOutOfTheFrameAndTheChatboxIsRebuiltWithItOnceAfterwards()
	{
		service.capture(captured::add);

		assertFalse(shownInChat(ChatMessageType.PUBLICCHAT));
		assertTrue(shownInChat(ChatMessageType.GAMEMESSAGE));
		assertEquals(List.of(REBUILD_CHAT), scriptsRun);

		scriptsRun.clear();
		drawFrame();

		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
		assertEquals(List.of(REBUILD_CHAT), scriptsRun);
	}

	@Test
	public void aClosedChatboxIsOpenedOnTheAllTabForTheFrameAndClosedAgainAfterwards()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;

		service.capture(captured::add);

		assertEquals(ScreenshotChat.ALL_TAB, chatView);
		drawFrame();

		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
		assertEquals(1, captured.size());
	}

	@Test
	public void openingAndClosingTheChatboxRebuildsTheChatOnlyThroughTheTabClicks()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;

		service.capture(captured::add);
		drawFrame();

		assertFalse(scriptsRun.contains(REBUILD_CHAT));
	}

	@Test
	public void aChatboxOnThePublicTabShowsAllForTheFrameAndThePublicTabAgainAfterwards()
	{
		chatView = PUBLIC_TAB;

		service.capture(captured::add);

		assertEquals(ScreenshotChat.ALL_TAB, chatView);
		drawFrame();

		assertEquals(PUBLIC_TAB, chatView);
	}

	@Test
	public void withPlayerChatShownAClosedChatboxIsOpenedOnTheGameTabSoNoPlayerChatAppears()
	{
		when(config.hidePlayerChat()).thenReturn(false);
		chatView = ScreenshotChat.CLOSED_CHATBOX;

		service.capture(captured::add);

		assertEquals(ScreenshotChat.GAME_TAB, chatView);
		drawFrame();

		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
	}

	@Test
	public void aChatboxAlreadyOnTheGameTabIsLeftAsItIs()
	{
		chatView = ScreenshotChat.GAME_TAB;

		service.capture(captured::add);
		drawFrame();

		assertEquals(ScreenshotChat.GAME_TAB, chatView);
		assertTrue(noChatTabClicked());
	}

	@Test
	public void aChatboxAlreadyOnTheAllTabIsLeftAsItIs()
	{
		service.capture(captured::add);
		drawFrame();

		assertEquals(ScreenshotChat.ALL_TAB, chatView);
		assertTrue(noChatTabClicked());
	}

	@Test
	public void aTabThePlayerPicksWhileTheFrameIsDrawnIsKept()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;
		service.capture(captured::add);

		chatView = PUBLIC_TAB;
		scriptsRun.clear();
		drawFrame();

		assertEquals(PUBLIC_TAB, chatView);
		assertTrue(noChatTabClicked());
	}

	@Test
	public void theChatIsPutBackWhenNoFrameIsDrawnWithinASecond()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;
		service.capture(captured::add);

		verify(executor).schedule(any(Runnable.class), eq(ScreenshotService.CHAT_RESTORE_TIMEOUT_MS), eq(TimeUnit.MILLISECONDS));
		timeouts.get(0).run();

		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
		verify(privateMessages).setHidden(false);
		assertTrue(captured.isEmpty());
	}

	@Test
	public void aFrameDrawnAfterTheChatWasPutBackIsNotPostedAndTheChatIsPreparedForAnotherFrame()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;
		service.capture(captured::add);
		timeouts.get(0).run();

		drawFrame();

		assertTrue(captured.isEmpty());
		assertEquals(ScreenshotChat.ALL_TAB, chatView);
		assertFalse(shownInChat(ChatMessageType.PUBLICCHAT));

		drawFrame();

		assertEquals(1, captured.size());
		assertNotNull(captured.get(0));
		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
	}

	@Test
	public void whenNoFrameComesInTimeThreeTimesThePostGoesWithoutAScreenshot()
	{
		service.capture(captured::add);
		for (int attempt = 0; attempt < ScreenshotService.FRAME_ATTEMPTS; attempt++)
		{
			timeouts.get(attempt).run();
			drawFrame();
		}

		assertEquals(1, captured.size());
		assertNull(captured.get(0));
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void withNothingToHideOrOpenALateFrameIsStillPosted()
	{
		turnEveryScreenshotOptionOff();
		service.capture(captured::add);
		timeouts.get(0).run();

		drawFrame();

		assertEquals(1, captured.size());
		assertNotNull(captured.get(0));
	}

	@Test
	public void aFrameTakenInTimeCallsOffTheOneSecondFallback()
	{
		service.capture(captured::add);
		drawFrame();

		verify(timeout).cancel(false);
	}

	@Test
	public void twoScreenshotsAtOnceKeepTheChatOpenUntilBothFramesAreTaken()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;
		service.capture(captured::add);
		service.capture(captured::add);

		drawFrame();

		assertEquals(ScreenshotChat.ALL_TAB, chatView);
		assertFalse(shownInChat(ChatMessageType.PUBLICCHAT));

		drawFrame();

		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
		assertEquals(2, captured.size());
	}

	@Test
	public void withAllThreeScreenshotOptionsOffAClosedChatboxStaysClosedAndPlayerChatStaysVisible()
	{
		turnEveryScreenshotOptionOff();
		chatView = ScreenshotChat.CLOSED_CHATBOX;

		service.capture(captured::add);
		drawFrame();

		assertTrue(scriptsRun.isEmpty());
		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
		verify(privateMessages, never()).setHidden(true);
		assertEquals(1, captured.size());
	}

	@Test
	public void aScreenshotTakenWhileLoggedOutLeavesTheChatAlone()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		chatView = ScreenshotChat.CLOSED_CHATBOX;

		service.capture(captured::add);
		drawFrame();

		assertTrue(scriptsRun.isEmpty());
		verify(privateMessages, never()).setHidden(true);
		assertEquals(1, captured.size());
	}

	@Test
	public void aLogoutOrWorldHopBeforeTheFrameLeavesTheChatToTheGame()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;
		service.capture(captured::add);
		when(client.getGameState()).thenReturn(GameState.HOPPING);
		scriptsRun.clear();

		drawFrame();

		assertTrue(scriptsRun.isEmpty());
		verify(privateMessages, never()).setHidden(false);
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void turningThePluginOffPutsTheChatBackAndDropsTheScreenshot()
	{
		chatView = ScreenshotChat.CLOSED_CHATBOX;
		service.capture(captured::add);

		service.shutDown();

		assertEquals(ScreenshotChat.CLOSED_CHATBOX, chatView);
		assertTrue(shownInChat(ChatMessageType.PUBLICCHAT));
		verify(timeout).cancel(false);

		drawFrame();

		assertTrue(captured.isEmpty());
		verify(drawManager, times(1)).requestNextFrameListener(any());
	}

	private void turnEveryScreenshotOptionOff()
	{
		when(config.hidePrivateMessages()).thenReturn(false);
		when(config.hidePlayerChat()).thenReturn(false);
		when(config.showGameChat()).thenReturn(false);
	}

	private void clickChatTabLikeTheGame(List<Object> script)
	{
		if (!script.get(0).equals(ScreenshotChat.CHAT_TAB_BUTTON_SCRIPT))
		{
			return;
		}
		int tab = (int) script.get(2);
		chatView = tab == chatView ? ScreenshotChat.CLOSED_CHATBOX : tab;
	}

	private boolean noChatTabClicked()
	{
		return scriptsRun.stream().noneMatch(script -> script.get(0).equals(ScreenshotChat.CHAT_TAB_BUTTON_SCRIPT));
	}

	private boolean shownInChat(ChatMessageType type)
	{
		return ChatLineCheck.shownInChat(client, service::onScriptCallbackEvent, type);
	}

	private void drawFrame()
	{
		ArgumentCaptor<Consumer<Image>> listener = ArgumentCaptor.forClass(Consumer.class);
		verify(drawManager, atLeastOnce()).requestNextFrameListener(listener.capture());
		listener.getAllValues().get(framesDrawn++).accept(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
	}
}
