package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScreenshotServiceTest
{
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
	private Widget chatbox;
	@Mock
	private Widget privateMessages;

	private ScreenshotService service;
	private final List<byte[]> captured = new ArrayList<>();

	@Before
	public void setUp()
	{
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).submit(any(Runnable.class));
		when(client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(chatbox);
		when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(privateMessages);
		service = new ScreenshotService(client, clientThread, drawManager, executor, config);
	}

	@Test
	public void privateMessagesAreHiddenWhileTheFrameIsDrawnAndShownAgainAfterwards()
	{
		when(config.chatInScreenshot()).thenReturn(ChatInScreenshot.HIDE_PRIVATE_MESSAGES);

		service.capture(captured::add);
		drawFrame();

		InOrder inOrder = inOrder(privateMessages, drawManager, clientThread);
		inOrder.verify(clientThread).invoke(any(Runnable.class));
		inOrder.verify(privateMessages).setHidden(true);
		inOrder.verify(drawManager).requestNextFrameListener(any());
		inOrder.verify(clientThread).invoke(any(Runnable.class));
		inOrder.verify(privateMessages).setHidden(false);
		verify(chatbox, never()).setHidden(true);
		assertEquals(1, captured.size());
		assertNotNull(captured.get(0));
	}

	@Test
	public void theWholeChatboxIsHiddenWhenTheSettingSaysSo()
	{
		when(config.chatInScreenshot()).thenReturn(ChatInScreenshot.HIDE_CHATBOX);

		service.capture(captured::add);
		drawFrame();

		verify(chatbox).setHidden(true);
		verify(chatbox).setHidden(false);
		verify(privateMessages).setHidden(true);
		verify(privateMessages).setHidden(false);
	}

	@Test
	public void theChatboxIsStillHiddenWhenThePrivateMessageInterfaceIsNotLoaded()
	{
		when(config.chatInScreenshot()).thenReturn(ChatInScreenshot.HIDE_CHATBOX);
		when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(null);

		service.capture(captured::add);
		drawFrame();

		verify(chatbox).setHidden(true);
		verify(chatbox).setHidden(false);
		assertEquals(1, captured.size());
	}

	@Test
	public void nothingIsHiddenWhenTheChatMayShow()
	{
		when(config.chatInScreenshot()).thenReturn(ChatInScreenshot.SHOW_ALL);

		service.capture(captured::add);
		drawFrame();

		verify(chatbox, never()).setHidden(true);
		verify(privateMessages, never()).setHidden(true);
		assertEquals(1, captured.size());
	}

	@Test
	public void aChatThePlayerHidThemselvesStaysHidden()
	{
		when(config.chatInScreenshot()).thenReturn(ChatInScreenshot.HIDE_CHATBOX);
		when(chatbox.isHidden()).thenReturn(true);

		service.capture(captured::add);
		drawFrame();

		verify(chatbox, never()).setHidden(true);
		verify(chatbox, never()).setHidden(false);
	}

	@Test
	public void theScreenshotIsStillTakenWhenTheChatInterfaceIsNotLoaded()
	{
		when(config.chatInScreenshot()).thenReturn(ChatInScreenshot.HIDE_PRIVATE_MESSAGES);
		when(client.getWidget(anyInt())).thenReturn(null);

		service.capture(captured::add);
		drawFrame();

		assertEquals(1, captured.size());
		assertNotNull(captured.get(0));
	}

	private void drawFrame()
	{
		ArgumentCaptor<Consumer<Image>> listener = ArgumentCaptor.forClass(Consumer.class);
		verify(drawManager).requestNextFrameListener(listener.capture());
		listener.getValue().accept(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
	}
}
