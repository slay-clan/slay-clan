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
