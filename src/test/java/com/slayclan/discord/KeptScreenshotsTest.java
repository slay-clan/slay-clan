package com.slayclan.discord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.util.Filepath;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KeptScreenshotsTest
{
	private static final String NONCE = "0123456789abcdef";
	private static final String OTHER_NONCE = "fedcba9876543210";
	private static final byte[] PNG = {1, 2, 3};
	private static final Instant NOW = Instant.now();

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private File folder;
	private KeptScreenshots screenshots;

	@Before
	public void setUp()
	{
		folder = new File(temporaryFolder.getRoot(), "pending-screenshots");
		screenshots = new KeptScreenshots(this::folder);
	}

	@Test
	public void aKeptScreenshotIsReadBackUnchanged()
	{
		screenshots.keep(NONCE, PNG);

		assertArrayEquals(PNG, screenshots.load(NONCE));
	}

	@Test
	public void aScreenshotThatWasNeverKeptReadsAsMissing()
	{
		assertNull(screenshots.load(NONCE));
	}

	@Test
	public void aDiscardedScreenshotIsGoneFromDisk()
	{
		screenshots.keep(NONCE, PNG);

		screenshots.discard(NONCE);

		assertEquals(List.of(), files());
	}

	@Test
	public void aPostIdThatIsNotInThePluginsOwnFormNeverBecomesAFile()
	{
		screenshots.keep("..", PNG);
		screenshots.keep("not-a-nonce", PNG);

		assertEquals(List.of(), files());
	}

	@Test
	public void aScreenshotKeptByAnotherClientOnTheMachineSurvivesTidyingUntilItIsTooOld() throws IOException
	{
		screenshots.keep(NONCE, PNG);
		File fromAnotherClient = new File(folder, OTHER_NONCE + ".png");
		Files.write(fromAnotherClient.toPath(), PNG);

		screenshots.prune(NOW);
		assertEquals(List.of(NONCE + ".png", OTHER_NONCE + ".png"), files());

		age(OTHER_NONCE, NOW.minus(KeptScreenshots.MAX_AGE).minusSeconds(60));
		screenshots.prune(NOW);
		assertEquals(List.of(NONCE + ".png"), files());
	}

	@Test
	public void keepingAScreenshotLeavesNoUnfinishedFileBesideIt()
	{
		screenshots.keep(NONCE, PNG);
		screenshots.keep(NONCE, PNG);

		assertEquals(List.of(NONCE + ".png"), files());
	}

	@Test
	public void tidyingDeletesAScreenshotOlderThanAPendingPostMayGet()
	{
		screenshots.keep(NONCE, PNG);
		screenshots.keep(OTHER_NONCE, PNG);
		age(NONCE, NOW.minus(KeptScreenshots.MAX_AGE).minusSeconds(60));

		screenshots.prune(NOW);

		assertEquals(List.of(OTHER_NONCE + ".png"), files());
	}

	@Test
	public void tidyingDeletesTheOldestScreenshotsOnceTheyTakeTooMuchSpace()
	{
		screenshots = new KeptScreenshots(this::folder, 2 * PNG.length);
		String newest = "aaaaaaaaaaaaaaaa";
		screenshots.keep(NONCE, PNG);
		screenshots.keep(OTHER_NONCE, PNG);
		screenshots.keep(newest, PNG);
		age(NONCE, NOW.minusSeconds(3600));
		age(OTHER_NONCE, NOW.minusSeconds(1800));

		screenshots.prune(NOW);

		assertEquals(List.of(newest + ".png", OTHER_NONCE + ".png"), files());
	}

	@Test
	public void tidyingBeforeAnythingWasKeptLeavesTheDiskUntouched()
	{
		screenshots.prune(NOW);

		assertEquals(List.of(), files());
	}

	private Filepath folder()
	{
		return Filepath.Unchecked.getRooted(folder.toPath());
	}

	private void age(String nonce, Instant modified)
	{
		new File(folder, nonce + ".png").setLastModified(modified.toEpochMilli());
	}

	private List<String> files()
	{
		String[] names = folder.list();
		if (names == null)
		{
			return List.of();
		}
		Arrays.sort(names);
		return List.of(names);
	}
}
