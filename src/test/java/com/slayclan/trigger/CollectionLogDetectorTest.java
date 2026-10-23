package com.slayclan.trigger;

import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class CollectionLogDetectorTest
{
	private final CollectionLogDetector detector = new CollectionLogDetector();

	@Test
	public void detectsNewItemWithColorTags()
	{
		assertEquals(Optional.of("Ancestral robe top"),
			detector.detect("New item added to your collection log: <col=ef1020>Ancestral robe top</col>"));
	}

	@Test
	public void detectsItemNameContainingParentheses()
	{
		assertEquals(Optional.of("Victor's cape (100)"), detector.detect("New item added to your collection log: Victor's cape (100)"));
	}

	@Test
	public void ignoresClanBroadcastAboutCollectionLog()
	{
		assertFalse(detector.detect("SkogsJonas received a new collection log item: Ancestral robe top (133/1717)").isPresent());
	}

	@Test
	public void ignoresNullMessage()
	{
		assertFalse(detector.detect(null).isPresent());
	}
}
