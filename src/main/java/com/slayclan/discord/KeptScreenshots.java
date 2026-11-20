package com.slayclan.discord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * The screenshots of posts waiting for a retry, one PNG per post in the plugin's data folder, named after the
 * post's nonce. The folder is shared by every client and profile on the machine while each keeps its own list
 * of waiting posts, so a file is never deleted for being unknown to the one that looks. Every method reads or
 * writes the disk, so none may be called on the client thread.
 */
@Slf4j
public class KeptScreenshots
{
	/** Where the files live. Asked on every use, since finding the plugin's data folder touches the disk too. */
	public interface Folder
	{
		Filepath locate() throws IOException;
	}

	// For the whole folder. Beyond it the oldest files go first, and their posts are retried without a screenshot
	static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
	static final Duration MAX_AGE = PendingPostStore.MAX_AGE;
	private static final String EXTENSION = ".png";
	private static final String UNFINISHED_EXTENSION = ".tmp";
	// A nonce read back from the profile becomes a file name, so anything but the plugin's own form is turned away
	private static final Pattern NONCE = Pattern.compile("[0-9a-f]{16}");

	private final Folder folder;
	private final long maxTotalBytes;

	public KeptScreenshots(Folder folder)
	{
		this(folder, MAX_TOTAL_BYTES);
	}

	KeptScreenshots(Folder folder, long maxTotalBytes)
	{
		this.folder = folder;
		this.maxTotalBytes = maxTotalBytes;
	}

	public void keep(String nonce, byte[] png)
	{
		if (!isNonce(nonce))
		{
			return;
		}
		try
		{
			Filepath directory = folder.locate();
			directory.createDirectories();
			writeWhole(directory, nonce, png);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Could not keep the screenshot of post {}, it is retried without one", nonce, e);
		}
	}

	// A client closed in the middle of a write would otherwise leave half a PNG under the name a retry reads
	private static void writeWhole(Filepath directory, String nonce, byte[] png) throws IOException
	{
		Filepath unfinished = directory.createTempFile(nonce, UNFINISHED_EXTENSION);
		try
		{
			unfinished.write(png);
			unfinished.moveTo(directory.joinSegment(fileName(nonce)), StandardCopyOption.REPLACE_EXISTING);
		}
		finally
		{
			unfinished.deleteIfExists();
		}
	}

	/** Null when the file is gone or cannot be read; the post is then sent without a screenshot. */
	public byte[] load(String nonce)
	{
		if (!isNonce(nonce))
		{
			return null;
		}
		try (InputStream in = folder.locate().joinSegment(fileName(nonce)).openInputStream())
		{
			return in.readAllBytes();
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("The kept screenshot of post {} is gone or unreadable, sending without it", nonce, e);
			return null;
		}
	}

	public void discard(String nonce)
	{
		if (!isNonce(nonce))
		{
			return;
		}
		try
		{
			folder.locate().joinSegment(fileName(nonce)).deleteIfExists();
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Could not delete the kept screenshot of post {}", nonce, e);
		}
	}

	/**
	 * Deletes every file older than a waiting post may get, then the oldest ones until the rest fits the size
	 * limit.
	 */
	public void prune(Instant now)
	{
		try
		{
			Filepath directory = folder.locate();
			if (!directory.isDirectory())
			{
				return;
			}
			List<KeptFile> fresh = new ArrayList<>();
			for (KeptFile file : filesIn(directory))
			{
				if (file.getModified().isAfter(now.minus(MAX_AGE)))
				{
					fresh.add(file);
				}
				else
				{
					file.getPath().deleteIfExists();
				}
			}
			deleteOldestBeyondTheSizeLimit(fresh);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Could not tidy the kept screenshots", e);
		}
	}

	private void deleteOldestBeyondTheSizeLimit(List<KeptFile> files) throws IOException
	{
		files.sort(Comparator.comparing(KeptFile::getModified).reversed());
		long total = 0;
		for (KeptFile file : files)
		{
			total += file.getSize();
			if (total > maxTotalBytes)
			{
				file.getPath().deleteIfExists();
			}
		}
	}

	private static List<KeptFile> filesIn(Filepath directory) throws IOException
	{
		List<Filepath> paths;
		try (Stream<Filepath> walk = directory.walk(1))
		{
			paths = walk.filter(Filepath::isFile).collect(Collectors.toList());
		}
		List<KeptFile> files = new ArrayList<>();
		for (Filepath path : paths)
		{
			files.add(new KeptFile(path, path.size(), path.getLastModifiedTime().toInstant()));
		}
		return files;
	}

	private static boolean isNonce(String nonce)
	{
		return nonce != null && NONCE.matcher(nonce).matches();
	}

	private static String fileName(String nonce)
	{
		return nonce + EXTENSION;
	}

	@Value
	private static class KeptFile
	{
		Filepath path;
		long size;
		Instant modified;
	}
}
