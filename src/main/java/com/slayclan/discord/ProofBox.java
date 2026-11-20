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
import net.runelite.client.ui.FontManager;

/**
 * Draws a box at the top of the game view in a screenshot that names what completed a task, with the item icons. It
 * is for posts judged from the game's own records, where the frame shows whatever the member had open and not the
 * proof. The image keeps the frame's size so Discord's preview does not shrink the chat staff read, and the box is
 * kept clear of the minimap and the side panel, in the top half of the game view.
 */
@Slf4j
public class ProofBox
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

		/** Only what the box has room for is turned into an entry, so no icon is loaded for an item left out. */
		public static <T> Proof of(List<T> completedBy, Function<T, Entry> toEntry)
		{
			List<Entry> shown = completedBy.stream().limit(MAX_ENTRIES).map(toEntry).collect(Collectors.toList());
			return new Proof(shown, completedBy.size() - shown.size());
		}
	}

	/**
	 * The free part of the game view, in the client's canvas pixels: the 3D view in fixed mode, and in resizable mode
	 * the canvas between margins wide enough to clear the minimap. The frame may be drawn larger than the canvas,
	 * as in stretched mode, so the area is scaled to the frame.
	 */
	@Value
	public static class Area
	{
		int x;
		int y;
		int width;
		int height;
		int canvasWidth;
		int canvasHeight;

		private Area scaledTo(BufferedImage frame)
		{
			double horizontal = (double) frame.getWidth() / canvasWidth;
			double vertical = (double) frame.getHeight() / canvasHeight;
			return new Area((int) (x * horizontal), (int) (y * vertical), (int) (width * horizontal), (int) (height * vertical),
				frame.getWidth(), frame.getHeight());
		}
	}

	// A task with more items says how many are left out
	static final int MAX_ENTRIES = 8;
	static final int PADDING = 8;
	static final int TOP_MARGIN = 8;
	// An item sprite is 32 high and is drawn at one and a half times that, so it reads like the box's larger text
	static final int ROW_HEIGHT = 56;
	private static final double ICON_ZOOM = 1.5;
	private static final float FONT_ZOOM = 1.5f;
	private static final int ICON_GAP = 6;
	private static final int ENTRY_GAP = 20;
	// Leaves the lower half of the game view, and the chat below it, for what staff read
	private static final double MAX_HEIGHT_SHARE = 0.5;
	// An area this narrow means the client reported no usable game view, so the box falls back to the whole frame
	private static final int MIN_AREA_WIDTH = 200;
	// From about a 4K window on the box is drawn larger, where its text would otherwise be too small to read
	static final int FRAME_WIDTH_PER_SIZE_STEP = 1900;
	static final int FRAME_HEIGHT_PER_SIZE_STEP = 1000;
	private static final String CUT_OFF = "...";
	private static final Color BACKGROUND = new Color(30, 30, 30, 220);
	private static final Color TEXT = Color.WHITE;
	private static final FontRenderContext PLAIN_TEXT = new FontRenderContext(null, false, false);

	@Value
	private static class Placed
	{
		Sized line;
		int x;
		int row;
	}

	/** One line of an entry as drawn: the icon only on its first line. */
	@Value
	private static class Sized
	{
		String label;
		BufferedImage icon;
		int width;
	}

	@Value
	private static class Box
	{
		int x;
		int y;
		int width;
		int height;
	}

	/**
	 * Returns a copy of the frame with the box drawn on it, or the frame as it is when there is nothing to show or
	 * the box cannot be drawn.
	 */
	public BufferedImage addTo(BufferedImage frame, Proof proof, Area area)
	{
		if (nothingToShow(frame, proof))
		{
			return frame;
		}
		try
		{
			return draw(frame, proof, usableArea(frame, area), sizeFor(frame));
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

	private static Area usableArea(BufferedImage frame, Area area)
	{
		Area whole = new Area(0, 0, frame.getWidth(), frame.getHeight(), frame.getWidth(), frame.getHeight());
		if (clientReportedNoCanvas(area))
		{
			return whole;
		}
		Area scaled = area.scaledTo(frame);
		return scaled.getWidth() < MIN_AREA_WIDTH ? whole : scaled;
	}

	private static boolean clientReportedNoCanvas(Area area)
	{
		return area.getCanvasWidth() <= 0 || area.getCanvasHeight() <= 0;
	}

	// An ultrawide window is no denser than a normal one, so the height decides as much as the width
	private static int sizeFor(BufferedImage frame)
	{
		return Math.max(1, Math.min(frame.getWidth() / FRAME_WIDTH_PER_SIZE_STEP, frame.getHeight() / FRAME_HEIGHT_PER_SIZE_STEP));
	}

	private static BufferedImage draw(BufferedImage frame, Proof proof, Area area, int size)
	{
		Font runescape = FontManager.getRunescapeFont();
		Font font = runescape.deriveFont(runescape.getSize() * FONT_ZOOM * size);
		int usable = area.getWidth() - 2 * PADDING * size;
		List<List<Sized>> lines = proof.getShown().stream().map(entry -> linesOf(entry, font, usable, size)).collect(Collectors.toList());
		List<List<Sized>> rows = rowsThatFit(lines, proof.getLeftOut(), font, usable, maxRows(area, size), size);
		Box box = boxFor(rows, area, size);
		BufferedImage result = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = result.createGraphics();
		try
		{
			graphics.drawImage(frame, 0, 0, null);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setColor(BACKGROUND);
			graphics.fillRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());
			graphics.setFont(font);
			graphics.setColor(TEXT);
			for (Placed line : layout(rows, box, size))
			{
				drawLine(graphics, line, box.getY() + (PADDING + line.getRow() * ROW_HEIGHT) * size, size);
			}
		}
		finally
		{
			graphics.dispose();
		}
		return result;
	}

	private static int maxRows(Area area, int size)
	{
		int room = (int) (area.getHeight() * MAX_HEIGHT_SHARE) - (TOP_MARGIN + 2 * PADDING) * size;
		return Math.max(1, room / (ROW_HEIGHT * size));
	}

	/**
	 * Shows as many entries as fit in the rows the box may use, counting the rest into the "+N more" entry. The first
	 * entry is always shown, so in a game view too low for it and the count they take a row more than allowed.
	 */
	private static List<List<Sized>> rowsThatFit(List<List<Sized>> lines, int leftOut, Font font, int usable, int maxRows, int size)
	{
		List<List<Sized>> rows = null;
		for (int count = lines.size(); count >= 1; count--)
		{
			List<List<Sized>> shown = new ArrayList<>(lines.subList(0, count));
			int notShown = leftOut + lines.size() - count;
			if (notShown > 0)
			{
				shown.add(linesOf(Entry.text("+" + notShown + " more"), font, usable, size));
			}
			rows = fillRows(shown, usable, size);
			if (rows.size() <= maxRows)
			{
				break;
			}
		}
		return rows;
	}

	private static Box boxFor(List<List<Sized>> rows, Area area, int size)
	{
		int contentWidth = rows.stream().mapToInt(row -> rowWidth(row, size)).max().orElse(0);
		int width = Math.min(area.getWidth(), contentWidth + 2 * PADDING * size);
		int height = (2 * PADDING + rows.size() * ROW_HEIGHT) * size;
		return new Box(area.getX() + (area.getWidth() - width) / 2, area.getY() + TOP_MARGIN * size, width, height);
	}

	private static List<Placed> layout(List<List<Sized>> rows, Box box, int size)
	{
		List<Placed> placed = new ArrayList<>();
		for (int row = 0; row < rows.size(); row++)
		{
			int x = box.getX() + (box.getWidth() - rowWidth(rows.get(row), size)) / 2;
			for (Sized line : rows.get(row))
			{
				placed.add(new Placed(line, x, row));
				x += line.getWidth() + ENTRY_GAP * size;
			}
		}
		return placed;
	}

	// An entry that wraps takes rows of its own, so its lines read as one name
	private static List<List<Sized>> fillRows(List<List<Sized>> entries, int usable, int size)
	{
		List<List<Sized>> rows = new ArrayList<>();
		List<Sized> row = new ArrayList<>();
		for (List<Sized> lines : entries)
		{
			if (lines.size() > 1)
			{
				addIfFilled(rows, row);
				lines.forEach(line -> rows.add(List.of(line)));
				row = new ArrayList<>();
				continue;
			}
			Sized line = lines.get(0);
			if (needsANewRow(row, line, usable, size))
			{
				rows.add(row);
				row = new ArrayList<>();
			}
			row.add(line);
		}
		addIfFilled(rows, row);
		return rows;
	}

	private static void addIfFilled(List<List<Sized>> rows, List<Sized> row)
	{
		if (!row.isEmpty())
		{
			rows.add(row);
		}
	}

	/**
	 * Breaks a name too wide for the box at spaces, since its end is often the proof itself, such as the time of a
	 * personal best or the name of a combat achievement. Only a single word wider than the box is cut short.
	 */
	private static List<Sized> linesOf(Entry entry, Font font, int usable, int size)
	{
		List<Sized> lines = new ArrayList<>();
		String line = "";
		for (String word : entry.getLabel().split(" "))
		{
			String longer = line.isEmpty() ? word : line + " " + word;
			BufferedImage icon = lines.isEmpty() ? entry.getIcon() : null;
			if (fitsOnTheLine(line, longer, icon, font, usable, size))
			{
				line = longer;
				continue;
			}
			lines.add(cutToFit(line, icon, font, usable, size));
			line = word;
		}
		lines.add(cutToFit(line, lines.isEmpty() ? entry.getIcon() : null, font, usable, size));
		return lines;
	}

	// A word that starts a line is always taken, so a single word wider than the box still gets a line to be cut on
	private static boolean fitsOnTheLine(String line, String longer, BufferedImage icon, Font font, int usable, int size)
	{
		return line.isEmpty() || widthOf(longer, icon, font, size) <= usable;
	}

	private static Sized cutToFit(String label, BufferedImage icon, Font font, int usable, int size)
	{
		String shown = label;
		String kept = label;
		while (tooWideToCutFurther(shown, kept, icon, font, usable, size))
		{
			kept = kept.substring(0, kept.length() - 1).trim();
			shown = kept + CUT_OFF;
		}
		return new Sized(shown, icon, widthOf(shown, icon, font, size));
	}

	private static boolean tooWideToCutFurther(String shown, String kept, BufferedImage icon, Font font, int usable, int size)
	{
		return widthOf(shown, icon, font, size) > usable && kept.length() > 1;
	}

	private static int widthOf(String label, BufferedImage icon, Font font, int size)
	{
		return iconSpace(icon, size) + (int) Math.ceil(font.getStringBounds(label, PLAIN_TEXT).getWidth());
	}

	private static boolean needsANewRow(List<Sized> row, Sized next, int usable, int size)
	{
		return !row.isEmpty() && rowWidth(row, size) + ENTRY_GAP * size + next.getWidth() > usable;
	}

	private static int rowWidth(List<Sized> row, int size)
	{
		return row.stream().mapToInt(Sized::getWidth).sum() + Math.max(0, row.size() - 1) * ENTRY_GAP * size;
	}

	private static int iconSpace(BufferedImage icon, int size)
	{
		return icon == null ? 0 : iconWidth(icon, size) + ICON_GAP * size;
	}

	private static int iconWidth(BufferedImage icon, int size)
	{
		return (int) Math.round(icon.getWidth() * ICON_ZOOM * size);
	}

	static int iconHeight(BufferedImage icon, int size)
	{
		return (int) Math.round(icon.getHeight() * ICON_ZOOM * size);
	}

	private static void drawLine(Graphics2D graphics, Placed placed, int rowTop, int size)
	{
		int rowHeight = ROW_HEIGHT * size;
		Sized line = placed.getLine();
		BufferedImage icon = line.getIcon();
		if (icon != null)
		{
			int iconHeight = iconHeight(icon, size);
			graphics.drawImage(icon, placed.getX(), rowTop + (rowHeight - iconHeight) / 2, iconWidth(icon, size), iconHeight, null);
		}
		FontMetrics metrics = graphics.getFontMetrics();
		int baseline = rowTop + (rowHeight - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(line.getLabel(), placed.getX() + iconSpace(icon, size), baseline);
	}
}
