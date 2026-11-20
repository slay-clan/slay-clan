package com.slayclan.discord;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ProofBoxTest
{
	private static final int FRAME_WIDTH = 765;
	private static final int FRAME_HEIGHT = 503;
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;
	private static final int ONE_ROW = 2 * ProofBox.PADDING + ProofBox.ROW_HEIGHT;
	// The fixed-size client: the 3D view is 512 wide from x 4, the side panel with the minimap to its right
	private static final ProofBox.Area FIXED_GAME_VIEW = new ProofBox.Area(4, 4, 512, 334, FRAME_WIDTH, FRAME_HEIGHT);

	private final ProofBox box = new ProofBox();
	private final BufferedImage frame = filled(FRAME_WIDTH, FRAME_HEIGHT, Color.BLUE);

	@Test
	public void theScreenshotKeepsItsSizeSoDiscordShrinksItNoMoreThanAnyOtherPost()
	{
		BufferedImage result = onWholeCanvas(proof(item("Abyssal whip"), item("Dragon defender")));

		assertEquals(FRAME_WIDTH, result.getWidth());
		assertEquals(FRAME_HEIGHT, result.getHeight());
	}

	@Test
	public void theOriginalScreenshotIsNotDrawnOn()
	{
		onWholeCanvas(proof(item("Abyssal whip")));

		assertEquals(Color.BLUE.getRGB(), frame.getRGB(FRAME_WIDTH / 2, ProofBox.TOP_MARGIN + ProofBox.PADDING));
	}

	@Test
	public void theBoxSitsAtTheTopCentreAndLeavesTheCornersFree()
	{
		BufferedImage result = onWholeCanvas(proof(item("Abyssal whip")));

		assertNotEquals(Color.BLUE.getRGB(), result.getRGB(FRAME_WIDTH / 2, ProofBox.TOP_MARGIN + 1));
		assertEquals(Color.BLUE.getRGB(), result.getRGB(0, ProofBox.TOP_MARGIN + 1));
		assertEquals(Color.BLUE.getRGB(), result.getRGB(FRAME_WIDTH - 1, ProofBox.TOP_MARGIN + 1));
		assertEquals(Color.BLUE.getRGB(), result.getRGB(FRAME_WIDTH / 2, 0));
		assertEquals(Color.BLUE.getRGB(), result.getRGB(FRAME_WIDTH / 2, ProofBox.TOP_MARGIN + ONE_ROW));
	}

	@Test
	public void theBoxShowsTheItemIconAndItsName()
	{
		BufferedImage result = onWholeCanvas(proof(item("Abyssal whip")));

		Span icon = redSpan(result);
		assertTrue("the icon is drawn", icon.found());
		assertTrue("the name is written after the icon", lastTextColumn(result) > icon.right);
	}

	@Test
	public void theItemIconIsDrawnLargerThanTheGameSprite()
	{
		BufferedImage result = onWholeCanvas(proof(item("Abyssal whip")));

		Span icon = redSpan(result);
		assertEquals(ProofBox.iconHeight(item("Abyssal whip").getIcon(), 1), icon.bottom - icon.top + 1);
		assertTrue(icon.bottom - icon.top + 1 > ICON_HEIGHT);
	}

	@Test
	public void theProofIsCenteredInTheGameView()
	{
		BufferedImage result = onWholeCanvas(proof(item("Abyssal whip")));

		int marginLeft = redSpan(result).left;
		int marginRight = FRAME_WIDTH - 1 - lastTextColumn(result);
		assertTrue("left margin " + marginLeft + " and right margin " + marginRight + " differ",
			Math.abs(marginLeft - marginRight) <= 4);
	}

	@Test
	public void inTheFixedSizeClientTheBoxStaysLeftOfTheSidePanelWithTheMinimap()
	{
		BufferedImage result = box.addTo(frame, proof(item("Abyssal whip"), item("Dragon defender"), item("Fire cape")), FIXED_GAME_VIEW);

		assertTrue("an icon is drawn", redSpan(result).found());
		assertTrue("the box ends inside the 3D view", lastDrawnColumn(result) < 516);
		assertTrue("the box starts inside the 3D view", firstDrawnColumn(result) >= 4);
	}

	@Test
	public void inTheFixedSizeClientManyLongItemNamesNeverCoverTheChat()
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < ProofBox.MAX_ENTRIES + 3; i++)
		{
			names.add("Sanguine torva platebody (damaged) " + i);
		}

		BufferedImage result = box.addTo(frame, ProofBox.Proof.of(names, ProofBoxTest::item), FIXED_GAME_VIEW);

		for (int y = FIXED_GAME_VIEW.getY() + FIXED_GAME_VIEW.getHeight() / 2; y < FRAME_HEIGHT; y++)
		{
			for (int x = 0; x < FRAME_WIDTH; x++)
			{
				assertEquals("pixel at " + x + "," + y, Color.BLUE.getRGB(), result.getRGB(x, y));
			}
		}
	}

	@Test
	public void itemsThatDoNotFitTheBoxAreCountedAsLeftOut()
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < ProofBox.MAX_ENTRIES; i++)
		{
			names.add("Sanguine torva platebody (damaged) " + i);
		}
		BufferedImage withoutCount = box.addTo(frame, ProofBox.Proof.of(names.subList(0, 1), ProofBoxTest::item), FIXED_GAME_VIEW);

		BufferedImage result = box.addTo(frame, ProofBox.Proof.of(names, ProofBoxTest::item), FIXED_GAME_VIEW);

		int secondRowTop = FIXED_GAME_VIEW.getY() + ProofBox.TOP_MARGIN + ProofBox.PADDING + ProofBox.ROW_HEIGHT;
		assertFalse(hasWhitePixel(withoutCount, secondRowTop));
		assertTrue("a \"+N more\" entry is written under the first item", hasWhitePixel(result, secondRowTop));
	}

	@Test
	public void aNameTooLongForTheGameViewContinuesOnTheNextRowSoItsEndWithTheTimeIsStillShown()
	{
		ProofBox.Area narrow = new ProofBox.Area(232, 0, 300, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
		BufferedImage oneLine = box.addTo(frame, ProofBox.Proof.text("Personal best: Zulrah"), narrow);

		BufferedImage result = box.addTo(frame, ProofBox.Proof.text("Personal best: Tombs of Amascut: Expert Mode 1:23:45"), narrow);

		int secondRowTop = ProofBox.TOP_MARGIN + ProofBox.PADDING + ProofBox.ROW_HEIGHT;
		assertFalse(hasWhitePixel(oneLine, secondRowTop));
		assertTrue("the rest of the name is written on the next row", hasWhitePixel(result, secondRowTop));
		assertTrue("the text stays inside the game view", lastTextColumn(result) < 532);
		assertTrue("the text stays inside the game view", firstDrawnColumn(result) >= 232);
	}

	@Test
	public void aSingleWordTooLongForTheGameViewIsCutShortInsteadOfCrossingIntoTheSidePanel()
	{
		BufferedImage result = box.addTo(frame, ProofBox.Proof.text("Supercalifragilisticexpialidocious".repeat(4)), FIXED_GAME_VIEW);

		assertTrue("text is written", lastTextColumn(result) >= 0);
		assertTrue("the text ends inside the 3D view", lastTextColumn(result) < 516);
	}

	@Test
	public void aGameViewTheClientCouldNotMeasureFallsBackToTheWholeScreenshot()
	{
		BufferedImage result = box.addTo(frame, proof(item("Abyssal whip")), new ProofBox.Area(0, 0, 0, 0, 0, 0));

		assertTrue("the proof is still drawn", redSpan(result).found());
	}

	@Test
	public void aCombatAchievementOrPersonalBestIsNamedWithoutAnIcon()
	{
		BufferedImage result = onWholeCanvas(ProofBox.Proof.text("Combat achievement: Perfect Olm"));

		assertEquals(1, rowsWithText(result));
		assertFalse("no icon is drawn", redSpan(result).found());
	}

	@Test
	public void aTaskWithMoreItemsThanTheBoxShowsNamesTheFirstOnesAndSaysHowManyAreLeftOut()
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < ProofBox.MAX_ENTRIES + 4; i++)
		{
			names.add("Item " + i);
		}
		List<String> iconsLoadedFor = new ArrayList<>();

		ProofBox.Proof proof = ProofBox.Proof.of(names, name ->
		{
			iconsLoadedFor.add(name);
			return item(name);
		});

		assertEquals(ProofBox.MAX_ENTRIES, proof.getShown().size());
		assertEquals(4, proof.getLeftOut());
		assertEquals(names.subList(0, ProofBox.MAX_ENTRIES), iconsLoadedFor);
	}

	@Test
	public void theLeftOutCountTakesAPlaceOfItsOwnInTheBox()
	{
		BufferedImage without = onWholeCanvas(new ProofBox.Proof(List.of(ProofBox.Entry.text("Abyssal whip")), 0));
		BufferedImage with = onWholeCanvas(new ProofBox.Proof(List.of(ProofBox.Entry.text("Abyssal whip")), 4));

		assertTrue(lastTextColumn(with) > lastTextColumn(without));
	}

	@Test
	public void theBoxIsDrawnAtTwiceTheSizeOnAScreenshotFromA4kWindow()
	{
		BufferedImage large = filled(2 * ProofBox.FRAME_WIDTH_PER_SIZE_STEP, 2 * ProofBox.FRAME_HEIGHT_PER_SIZE_STEP, Color.BLUE);

		BufferedImage result = box.addTo(large, proof(item("Abyssal whip")), wholeCanvas(large.getWidth(), large.getHeight()));

		Span icon = redSpan(result);
		assertEquals(ProofBox.iconHeight(item("Abyssal whip").getIcon(), 2), icon.bottom - icon.top + 1);
	}

	@Test
	public void anUltrawideWindowOfNormalHeightKeepsTheNormalSize()
	{
		BufferedImage ultrawide = filled(2 * ProofBox.FRAME_WIDTH_PER_SIZE_STEP, FRAME_HEIGHT, Color.BLUE);

		BufferedImage result = box.addTo(ultrawide, proof(item("Abyssal whip")), wholeCanvas(ultrawide.getWidth(), ultrawide.getHeight()));

		Span icon = redSpan(result);
		assertEquals(ProofBox.iconHeight(item("Abyssal whip").getIcon(), 1), icon.bottom - icon.top + 1);
	}

	@Test
	public void aStretchedFrameGetsTheBoxWhereTheGameViewIsDrawn()
	{
		BufferedImage stretched = filled(2 * FRAME_WIDTH, 2 * FRAME_HEIGHT, Color.BLUE);

		BufferedImage result = box.addTo(stretched, proof(item("Abyssal whip")), FIXED_GAME_VIEW);

		assertTrue("the box ends inside the stretched 3D view", lastDrawnColumn(result) < 2 * 516);
		assertTrue("the box is centred on the stretched 3D view", Math.abs((firstDrawnColumn(result) + lastDrawnColumn(result)) / 2 - 2 * 260) <= 4);
	}

	@Test
	public void aPostWithNothingToProveKeepsThePlainFrame()
	{
		assertSame(frame, onWholeCanvas(proof()));
		assertSame(frame, onWholeCanvas(null));
	}

	@Test
	public void aFrameThatCouldNotBeCapturedStaysMissing()
	{
		assertNull(box.addTo(null, proof(item("Abyssal whip")), wholeCanvas(FRAME_WIDTH, FRAME_HEIGHT)));
	}

	private static ProofBox.Area wholeCanvas(int width, int height)
	{
		return new ProofBox.Area(0, 0, width, height, width, height);
	}

	private BufferedImage onWholeCanvas(ProofBox.Proof proof)
	{
		return box.addTo(frame, proof, wholeCanvas(FRAME_WIDTH, FRAME_HEIGHT));
	}

	private static ProofBox.Proof proof(ProofBox.Entry... entries)
	{
		return new ProofBox.Proof(List.of(entries), 0);
	}

	private static ProofBox.Entry item(String name)
	{
		return new ProofBox.Entry(name, filled(ICON_WIDTH, ICON_HEIGHT, Color.RED));
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

	private static class Span
	{
		int left = Integer.MAX_VALUE;
		int right = -1;
		int top = Integer.MAX_VALUE;
		int bottom = -1;

		boolean found()
		{
			return right >= 0;
		}
	}

	// Icons are the only thing drawn in red
	private static Span redSpan(BufferedImage result)
	{
		Span span = new Span();
		for (int x = 0; x < result.getWidth(); x++)
		{
			for (int y = 0; y < result.getHeight(); y++)
			{
				if (result.getRGB(x, y) == Color.RED.getRGB())
				{
					span.left = Math.min(span.left, x);
					span.right = Math.max(span.right, x);
					span.top = Math.min(span.top, y);
					span.bottom = Math.max(span.bottom, y);
				}
			}
		}
		return span;
	}

	private static int firstDrawnColumn(BufferedImage result)
	{
		for (int x = 0; x < result.getWidth(); x++)
		{
			if (columnDrawnOn(result, x))
			{
				return x;
			}
		}
		return -1;
	}

	private static int lastDrawnColumn(BufferedImage result)
	{
		for (int x = result.getWidth() - 1; x >= 0; x--)
		{
			if (columnDrawnOn(result, x))
			{
				return x;
			}
		}
		return -1;
	}

	private static boolean columnDrawnOn(BufferedImage result, int x)
	{
		for (int y = 0; y < result.getHeight(); y++)
		{
			if (result.getRGB(x, y) != Color.BLUE.getRGB())
			{
				return true;
			}
		}
		return false;
	}

	// Labels are the only thing drawn in white, so a white pixel in a row means a name was written there
	private static int rowsWithText(BufferedImage result)
	{
		int rows = 0;
		for (int top = 0; top < result.getHeight(); top += ProofBox.ROW_HEIGHT)
		{
			rows += hasWhitePixel(result, top) ? 1 : 0;
		}
		return rows;
	}

	private static int lastTextColumn(BufferedImage result)
	{
		for (int x = result.getWidth() - 1; x >= 0; x--)
		{
			for (int y = 0; y < result.getHeight(); y++)
			{
				if (result.getRGB(x, y) == Color.WHITE.getRGB())
				{
					return x;
				}
			}
		}
		return -1;
	}

	private static boolean hasWhitePixel(BufferedImage result, int top)
	{
		for (int x = 0; x < result.getWidth(); x++)
		{
			for (int y = top; y < Math.min(result.getHeight(), top + ProofBox.ROW_HEIGHT); y++)
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
