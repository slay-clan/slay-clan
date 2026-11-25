package com.slayclan.activity;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Value;

/** Categories in order bosses, raids, clues, minigames, other. */
@Value
public class CollectionLogStatus
{
	int obtained;
	int max;
	int[] categoryObtained;
	int[] categoryMax;

	/**
	 * The server sends the log counts only while Character summary is shown, so after a login on another tab they all
	 * read 0 until the player opens that tab.
	 */
	public boolean isSentByGame()
	{
		return max > 0;
	}

	public String slotsText()
	{
		return obtained + "/" + max;
	}

	public String categoriesText()
	{
		return IntStream.range(0, categoryObtained.length)
			.mapToObj(i -> categoryObtained[i] + "/" + categoryMax[i])
			.collect(Collectors.joining(", "));
	}
}
