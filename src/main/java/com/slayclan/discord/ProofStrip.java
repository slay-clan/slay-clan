package com.slayclan.discord;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Adds a strip under a screenshot that names what completed a task, with the item icons. It is for posts
 * judged from the game's own records, where the frame shows whatever the member had open and not the proof.
 * The frame itself is left whole, since staff read the chat and the interfaces in it too.
 */
@Slf4j
public class ProofStrip
{
	@Value
	public static class Entry
	{
		String label;
		/** Null when the proof is no item, such as a combat achievement or a personal best. */
		BufferedImage icon;

		public static Entry text(String label)
		{
			return new Entry(label, null);
		}
	}

	@Value
	public static class Proof
	{
		List<Entry> shown;
		int leftOut;

		public static Proof text(String label)
		{
			return new Proof(List.of(Entry.text(label)), 0);
		}

		/** Only what the strip has room for is turned into an entry, so no icon is loaded for an item left out. */
		public static <T> Proof of(List<T> completedBy, Function<T, Entry> toEntry)
		{
			List<Entry> shown = completedBy.stream().limit(MAX_ENTRIES).map(toEntry).collect(Collectors.toList());
			return new Proof(shown, completedBy.size() - shown.size());
		}
	}

	// Two rows in a fixed-size client window; a task with more items says how many are left out
	static final int MAX_ENTRIES = 8;
	static final int PADDING = 8;
	// An item sprite is 32 high and is drawn at one and a half times that, so it reads like the strip's larger text
	static final int ROW_HEIGHT = 56;
	static final double ICON_ZOOM = 1.5;
	private static final float FONT_ZOOM = 1.5f;
	private static final int ICON_GAP = 6;
	private static final int ENTRY_GAP = 20;
	// From about 4K on the strip is drawn at twice the size, where its text would otherwise be too small to read
	static final int FRAME_WIDTH_PER_SIZE_STEP = 1900;
	static final Color BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT = Color.WHITE;
	private static final FontRenderContext PLAIN_TEXT = new FontRenderContext(null, false, false);

	@Value
	private static class Placed
	{
		Entry entry;
		int x;
		int row;
	}

	@Value
	private static class Sized
	{
		Entry entry;
		int width;
	}

	/**
	 * Returns a new image with the strip under the frame, or the frame as it is when there is nothing to show
	 * or the strip cannot be drawn.
	 */
	public BufferedImage addTo(BufferedImage frame, Proof proof)
	{
		if (nothingToShow(frame, proof))
		{
			return frame;
		}
		try
		{
			return draw(frame, entriesOf(proof), Math.max(1, frame.getWidth() / FRAME_WIDTH_PER_SIZE_STEP));
		}
		catch (RuntimeException e)
		{
			log.warn("Could not draw what completed the task, posting the plain screenshot", e);
			return frame;
		}
	}

	private static boolean nothingToShow(BufferedImage frame, Proof proof)
	{
		return frame == null || proof == null || proof.getShown().isEmpty();
	}

	private static List<Entry> entriesOf(Proof proof)
	{
		List<Entry> entries = new ArrayList<>(proof.getShown());
		if (proof.getLeftOut() > 0)
		{
			entries.add(Entry.text("+" + proof.getLeftOut() + " more"));
		}
		return entries;
	}

	private static BufferedImage draw(BufferedImage frame, List<Entry> entries, int size)
	{
		Font runescape = FontManager.getRunescapeFont();
		Font font = runescape.deriveFont(runescape.getSize() * FONT_ZOOM * size);
		List<Placed> placed = layout(entries, font, frame.getWidth(), size);
		int rows = placed.get(placed.size() - 1).getRow() + 1;
		int stripHeight = (2 * PADDING + rows * ROW_HEIGHT) * size;
		BufferedImage result = new BufferedImage(frame.getWidth(), frame.getHeight() + stripHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = result.createGraphics();
		try
		{
			graphics.drawImage(frame, 0, 0, null);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setColor(BACKGROUND);
			graphics.fillRect(0, frame.getHeight(), frame.getWidth(), stripHeight);
			graphics.setFont(font);
			graphics.setColor(TEXT);
			for (Placed entry : placed)
			{
				drawEntry(graphics, entry, frame.getHeight() + (PADDING + entry.getRow() * ROW_HEIGHT) * size, size);
			}
		}
		finally
		{
			graphics.dispose();
		}
		return result;
	}

	private static List<Placed> layout(List<Entry> entries, Font font, int width, int size)
	{
		List<Placed> placed = new ArrayList<>();
		List<List<Sized>> rows = fillRows(entries, font, width, size);
		for (int row = 0; row < rows.size(); row++)
		{
			int x = (width - rowWidth(rows.get(row), size)) / 2;
			for (Sized sized : rows.get(row))
			{
				placed.add(new Placed(sized.getEntry(), Math.max(PADDING * size, x), row));
				x += sized.getWidth() + ENTRY_GAP * size;
			}
		}
		return placed;
	}

	private static List<List<Sized>> fillRows(List<Entry> entries, Font font, int width, int size)
	{
		List<List<Sized>> rows = new ArrayList<>();
		List<Sized> row = new ArrayList<>();
		int usable = width - 2 * PADDING * size;
		for (Entry entry : entries)
		{
			Sized sized = new Sized(entry, iconSpace(entry, size) + (int) Math.ceil(font.getStringBounds(entry.getLabel(), PLAIN_TEXT).getWidth()));
			if (needsANewRow(row, sized, usable, size))
			{
				rows.add(row);
				row = new ArrayList<>();
			}
			row.add(sized);
		}
		rows.add(row);
		return rows;
	}

	// An entry wider than the whole strip stays on the row it starts, rather than leaving an empty row above it
	private static boolean needsANewRow(List<Sized> row, Sized next, int usable, int size)
	{
		return !row.isEmpty() && rowWidth(row, size) + ENTRY_GAP * size + next.getWidth() > usable;
	}

	private static int rowWidth(List<Sized> row, int size)
	{
		return row.stream().mapToInt(Sized::getWidth).sum() + Math.max(0, row.size() - 1) * ENTRY_GAP * size;
	}

	private static int iconSpace(Entry entry, int size)
	{
		return entry.getIcon() == null ? 0 : iconWidth(entry.getIcon(), size) + ICON_GAP * size;
	}

	static int iconWidth(BufferedImage icon, int size)
	{
		return (int) Math.round(icon.getWidth() * ICON_ZOOM * size);
	}

	static int iconHeight(BufferedImage icon, int size)
	{
		return (int) Math.round(icon.getHeight() * ICON_ZOOM * size);
	}

	private static void drawEntry(Graphics2D graphics, Placed placed, int rowTop, int size)
	{
		int rowHeight = ROW_HEIGHT * size;
		BufferedImage icon = placed.getEntry().getIcon();
		if (icon != null)
		{
			int iconHeight = iconHeight(icon, size);
			graphics.drawImage(icon, placed.getX(), rowTop + (rowHeight - iconHeight) / 2, iconWidth(icon, size), iconHeight, null);
		}
		FontMetrics metrics = graphics.getFontMetrics();
		int baseline = rowTop + (rowHeight - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(placed.getEntry().getLabel(), placed.getX() + iconSpace(placed.getEntry(), size), baseline);
	}
}
