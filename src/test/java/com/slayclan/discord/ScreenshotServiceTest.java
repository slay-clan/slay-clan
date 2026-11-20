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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
		when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(privateMessages);
		when(config.hidePrivateMessages()).thenReturn(true);
		service = new ScreenshotService(client, clientThread, drawManager, executor, config);
	}

	@Test
	public void privateMessagesAreHiddenWhileTheFrameIsDrawnAndShownAgainAfterwards()
	{
		service.capture(captured::add);
		drawFrame();

		InOrder inOrder = inOrder(privateMessages, drawManager, clientThread);
		inOrder.verify(clientThread).invoke(any(Runnable.class));
		inOrder.verify(privateMessages).setHidden(true);
		inOrder.verify(drawManager).requestNextFrameListener(any());
		inOrder.verify(clientThread).invoke(any(Runnable.class));
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
		verify(clientThread, times(1)).invoke(any(Runnable.class));
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

	private void drawFrame()
	{
		ArgumentCaptor<Consumer<Image>> listener = ArgumentCaptor.forClass(Consumer.class);
		verify(drawManager).requestNextFrameListener(listener.capture());
		listener.getValue().accept(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
	}
}
