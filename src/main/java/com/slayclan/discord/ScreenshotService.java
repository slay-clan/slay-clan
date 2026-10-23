package com.slayclan.discord;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class ScreenshotService
{
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;

	@Inject
	public ScreenshotService(DrawManager drawManager, ScheduledExecutorService executor)
	{
		this.drawManager = drawManager;
		this.executor = executor;
	}

	public void capture(Consumer<byte[]> onCaptured)
	{
		drawManager.requestNextFrameListener(image -> executor.submit(() -> onCaptured.accept(toPng(image))));
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
