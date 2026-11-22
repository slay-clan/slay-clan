package com.slayclan.discord;

import com.slayclan.SlayClanConfig;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class ScreenshotService
{
	// The player's chat is put back even when no frame is drawn; a frame drawn later is not posted, as it may show the chat
	static final long CHAT_RESTORE_TIMEOUT_MS = 1000;
	static final int FRAME_ATTEMPTS = 3;

	private final ClientThread clientThread;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;
	private final ScreenshotChat chat;
	private final Set<HeldChat> held = ConcurrentHashMap.newKeySet();
	private final AtomicInteger generations = new AtomicInteger();

	@Inject
	public ScreenshotService(Client client, ClientThread clientThread, DrawManager drawManager,
		ScheduledExecutorService executor, SlayClanConfig config)
	{
		this.clientThread = clientThread;
		this.drawManager = drawManager;
		this.executor = executor;
		this.chat = new ScreenshotChat(client, config);
	}

	public void capture(Consumer<byte[]> onCaptured)
	{
		int generation = generations.get();
		// Later rather than now, since the chat scripts must not run inside the game's own script or event that asked for the screenshot
		clientThread.invokeLater(() -> takeFrame(onCaptured, FRAME_ATTEMPTS, generation));
	}

	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		chat.onScriptCallbackEvent(event);
	}

	public void shutDown()
	{
		generations.incrementAndGet();
		List.copyOf(held).forEach(heldChat -> heldChat.release(clientThread::invoke));
	}

	private void takeFrame(Consumer<byte[]> onCaptured, int attemptsLeft, int generation)
	{
		if (generation != generations.get())
		{
			return;
		}
		HeldChat heldChat = holdChat();
		drawManager.requestNextFrameListener(image -> onFrame(image, heldChat, onCaptured, attemptsLeft, generation));
	}

	private HeldChat holdChat()
	{
		chat.hold();
		HeldChat heldChat = new HeldChat(chat.changesTheChat());
		heldChat.timeout = executor.schedule(heldChat::timeOut, CHAT_RESTORE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		held.add(heldChat);
		return heldChat;
	}

	private void onFrame(Image image, HeldChat heldChat, Consumer<byte[]> onCaptured, int attemptsLeft, int generation)
	{
		if (generation != generations.get())
		{
			return;
		}
		boolean drawnWhileHeld = heldChat.release(clientThread::invokeLater);
		if (showsTheChatAsPrepared(drawnWhileHeld, heldChat.changesTheChat))
		{
			executor.submit(() -> onCaptured.accept(toPng(image)));
		}
		else if (attemptsLeft > 1)
		{
			clientThread.invokeLater(() -> takeFrame(onCaptured, attemptsLeft - 1, generation));
		}
		else
		{
			log.debug("No frame came while the chat was prepared, posting without image");
			executor.submit(() -> onCaptured.accept(null));
		}
	}

	private static boolean showsTheChatAsPrepared(boolean drawnWhileHeld, boolean changesTheChat)
	{
		return drawnWhileHeld || !changesTheChat;
	}

	private final class HeldChat
	{
		private final boolean changesTheChat;
		private final AtomicBoolean released = new AtomicBoolean();
		private ScheduledFuture<?> timeout;

		private HeldChat(boolean changesTheChat)
		{
			this.changesTheChat = changesTheChat;
		}

		void timeOut()
		{
			release(clientThread::invoke);
		}

		/** Returns false when the chat was already let go of, by the timeout or at shutdown. */
		boolean release(Consumer<Runnable> onClientThread)
		{
			if (!released.compareAndSet(false, true))
			{
				return false;
			}
			timeout.cancel(false);
			held.remove(this);
			onClientThread.accept(chat::release);
			return true;
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
