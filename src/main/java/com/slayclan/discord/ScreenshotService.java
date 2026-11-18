package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class ScreenshotService
{
	private final Client client;
	private final ClientThread clientThread;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;
	private final SlayClanConfig config;

	@Inject
	public ScreenshotService(Client client, ClientThread clientThread, DrawManager drawManager,
		ScheduledExecutorService executor, SlayClanConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.drawManager = drawManager;
		this.executor = executor;
		this.config = config;
	}

	public void capture(Consumer<byte[]> onCaptured)
	{
		clientThread.invoke(() ->
		{
			List<Integer> hidden = hideChat(config.chatInScreenshot());
			drawManager.requestNextFrameListener(image ->
			{
				clientThread.invoke(() -> show(hidden));
				executor.submit(() -> onCaptured.accept(toPng(image)));
			});
		});
	}

	private List<Integer> hideChat(ChatInScreenshot mode)
	{
		List<Integer> hidden = new ArrayList<>();
		for (int componentId : componentsHiddenBy(mode))
		{
			Widget widget = client.getWidget(componentId);
			if (isShowing(widget))
			{
				widget.setHidden(true);
				hidden.add(componentId);
			}
		}
		return hidden;
	}

	private static boolean isShowing(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static List<Integer> componentsHiddenBy(ChatInScreenshot mode)
	{
		switch (mode)
		{
			// Split private messages are their own interface above the chatbox, so hiding the chatbox alone leaves them
			case HIDE_CHATBOX:
				return Arrays.asList(InterfaceID.Chatbox.CHATAREA, InterfaceID.PmChat.CONTAINER);
			case HIDE_PRIVATE_MESSAGES:
				return Collections.singletonList(InterfaceID.PmChat.CONTAINER);
			default:
				return Collections.emptyList();
		}
	}

	// Looked up again rather than kept, since the game may have rebuilt the interface while the frame was drawn
	private void show(List<Integer> componentIds)
	{
		for (int componentId : componentIds)
		{
			Widget widget = client.getWidget(componentId);
			if (widget != null)
			{
				widget.setHidden(false);
			}
		}
	}

	private static byte[] toPng(Image image)
	{
		try
		{
			BufferedImage buffered = ImageUtil.bufferedImageFromImage(image);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(buffered, "png", out);
			return out.toByteArray();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Screenshot failed, posting without image", e);
			return null;
		}
	}
}
