package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
		captureFrame(frame -> onCaptured.accept(toPng(frame)));
	}

	/** Hands over the frame itself, or null when it could not be read, on the executor. */
	public void captureFrame(Consumer<BufferedImage> onCaptured)
	{
		clientThread.invoke(() ->
		{
			boolean hidden = hidePrivateMessages();
			drawManager.requestNextFrameListener(image ->
			{
				if (hidden)
				{
					clientThread.invoke(this::showPrivateMessages);
				}
				executor.submit(() -> onCaptured.accept(buffered(image)));
			});
		});
	}

	private boolean hidePrivateMessages()
	{
		Widget privateMessages = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (nothingToHide(privateMessages))
		{
			return false;
		}
		privateMessages.setHidden(true);
		return true;
	}

	private boolean nothingToHide(Widget privateMessages)
	{
		return !config.hidePrivateMessages() || !isShowing(privateMessages);
	}

	private static boolean isShowing(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	// Looked up again rather than kept, since the game may have rebuilt the interface while the frame was drawn
	private void showPrivateMessages()
	{
		Widget privateMessages = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (privateMessages != null)
		{
			privateMessages.setHidden(false);
		}
	}

	private static BufferedImage buffered(Image image)
	{
		try
		{
			return ImageUtil.bufferedImageFromImage(image);
		}
		catch (RuntimeException e)
		{
			log.warn("Screenshot failed, posting without image", e);
			return null;
		}
	}

	public static byte[] toPng(BufferedImage frame)
	{
		if (frame == null)
		{
			return null;
		}
		try
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(frame, "png", out);
			return out.toByteArray();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Screenshot could not be encoded, posting without image", e);
			return null;
		}
	}
}
