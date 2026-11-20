package com.slayclan.discord;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ProofStripTest
{
	private static final int FRAME_WIDTH = 765;
	private static final int FRAME_HEIGHT = 503;
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;
	private static final int ONE_ROW = 2 * ProofStrip.PADDING + ProofStrip.ROW_HEIGHT;

	private final ProofStrip strip = new ProofStrip();
	private final BufferedImage frame = filled(FRAME_WIDTH, FRAME_HEIGHT, Color.BLUE);

	@Test
	public void theStripIsAddedUnderTheFrameAtItsFullWidth()
	{
		BufferedImage result = strip.addTo(frame, proof(item("Abyssal whip"), item("Dragon defender")));

		assertEquals(FRAME_WIDTH, result.getWidth());
		assertEquals(FRAME_HEIGHT + ONE_ROW, result.getHeight());
	}

	@Test
	public void theCapturedFrameIsShownWholeAboveTheStripAndIsItselfLeftAsItWas()
	{
		BufferedImage result = strip.addTo(frame, proof(item("Abyssal whip")));

		assertEquals(Color.BLUE.getRGB(), result.getRGB(0, 0));
		assertEquals(Color.BLUE.getRGB(), result.getRGB(FRAME_WIDTH - 1, FRAME_HEIGHT - 1));
		assertEquals(FRAME_HEIGHT, frame.getHeight());
	}

	@Test
	public void theStripShowsTheItemIconAndItsNameOnADarkBackground()
	{
		BufferedImage result = strip.addTo(frame, proof(item("Abyssal whip")));

		int iconTop = FRAME_HEIGHT + ProofStrip.PADDING + (ProofStrip.ROW_HEIGHT - ICON_HEIGHT) / 2;
		assertEquals(ProofStrip.BACKGROUND.getRGB(), result.getRGB(FRAME_WIDTH - 1, FRAME_HEIGHT));
		assertEquals(Color.RED.getRGB(), result.getRGB(ProofStrip.PADDING, iconTop));
		assertEquals(1, rowsWithText(result, ProofStrip.PADDING + ICON_WIDTH));
	}

	@Test
	public void aCombatAchievementOrPersonalBestIsNamedWithoutAnIcon()
	{
		BufferedImage result = strip.addTo(frame, ProofStrip.Proof.text("Combat achievement: Perfect Olm"));

		assertEquals(FRAME_HEIGHT + ONE_ROW, result.getHeight());
		assertEquals(1, rowsWithText(result, 0));
	}

	@Test
	public void itemsThatDoNotFitOneRowContinueOnTheNext()
	{
		List<ProofStrip.Entry> items = new ArrayList<>();
		for (int i = 0; i < ProofStrip.MAX_ENTRIES; i++)
		{
			items.add(item("Sanguine torva platebody (damaged)"));
		}

		BufferedImage result = strip.addTo(frame, ProofStrip.Proof.of(items, item -> item));

		int rows = (result.getHeight() - FRAME_HEIGHT - 2 * ProofStrip.PADDING) / ProofStrip.ROW_HEIGHT;
		assertTrue("expected several rows but got " + rows, rows > 1);
		assertEquals(rows, rowsWithText(result, 0));
	}

	@Test
	public void aTaskWithMoreItemsThanTheStripShowsNamesTheFirstOnesAndSaysHowManyAreLeftOut()
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < ProofStrip.MAX_ENTRIES + 4; i++)
		{
			names.add("Item " + i);
		}
		List<String> iconsLoadedFor = new ArrayList<>();

		ProofStrip.Proof proof = ProofStrip.Proof.of(names, name ->
		{
			iconsLoadedFor.add(name);
			return item(name);
		});

		assertEquals(ProofStrip.MAX_ENTRIES, proof.getShown().size());
		assertEquals(4, proof.getLeftOut());
		assertEquals(names.subList(0, ProofStrip.MAX_ENTRIES), iconsLoadedFor);
	}

	@Test
	public void theLeftOutCountTakesAPlaceOfItsOwnInTheStrip()
	{
		BufferedImage without = strip.addTo(frame, new ProofStrip.Proof(List.of(ProofStrip.Entry.text("Abyssal whip")), 0));
		BufferedImage with = strip.addTo(frame, new ProofStrip.Proof(List.of(ProofStrip.Entry.text("Abyssal whip")), 4));

		assertTrue(lastTextColumn(with) > lastTextColumn(without));
	}

	@Test
	public void theStripIsDrawnAtTwiceTheSizeUnderAFrameFromAVeryLargeWindow()
	{
		BufferedImage large = filled(2 * ProofStrip.FRAME_WIDTH_PER_SIZE_STEP, FRAME_HEIGHT, Color.BLUE);

		BufferedImage result = strip.addTo(large, proof(item("Abyssal whip")));

		assertEquals(FRAME_HEIGHT + 2 * ONE_ROW, result.getHeight());
		assertEquals(Color.RED.getRGB(), result.getRGB(2 * ProofStrip.PADDING + 2 * ICON_WIDTH - 1, FRAME_HEIGHT + ONE_ROW));
	}

	@Test
	public void aPostWithNothingToProveKeepsThePlainFrame()
	{
		assertSame(frame, strip.addTo(frame, proof()));
		assertSame(frame, strip.addTo(frame, null));
	}

	@Test
	public void aFrameThatCouldNotBeCapturedStaysMissing()
	{
		assertNull(strip.addTo(null, proof(item("Abyssal whip"))));
	}

	private static ProofStrip.Proof proof(ProofStrip.Entry... entries)
	{
		return new ProofStrip.Proof(List.of(entries), 0);
	}

	private static ProofStrip.Entry item(String name)
	{
		return new ProofStrip.Entry(name, filled(ICON_WIDTH, ICON_HEIGHT, Color.RED));
	}

	private static BufferedImage filled(int width, int height, Color color)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				image.setRGB(x, y, color.getRGB());
			}
		}
		return image;
	}

	// Labels are the only thing drawn in white, so a white pixel in a row means a name was written there
	private static int rowsWithText(BufferedImage result, int fromX)
	{
		int rows = 0;
		for (int top = FRAME_HEIGHT + ProofStrip.PADDING; top < result.getHeight() - ProofStrip.PADDING; top += ProofStrip.ROW_HEIGHT)
		{
			rows += hasWhitePixel(result, fromX, top) ? 1 : 0;
		}
		return rows;
	}

	private static int lastTextColumn(BufferedImage result)
	{
		for (int x = result.getWidth() - 1; x >= 0; x--)
		{
			for (int y = FRAME_HEIGHT; y < result.getHeight(); y++)
			{
				if (result.getRGB(x, y) == Color.WHITE.getRGB())
				{
					return x;
				}
			}
		}
		return -1;
	}

	private static boolean hasWhitePixel(BufferedImage result, int fromX, int top)
	{
		for (int x = fromX; x < result.getWidth(); x++)
		{
			for (int y = top; y < top + ProofStrip.ROW_HEIGHT; y++)
			{
				if (result.getRGB(x, y) == Color.WHITE.getRGB())
				{
					return true;
				}
			}
		}
		return false;
	}
}
