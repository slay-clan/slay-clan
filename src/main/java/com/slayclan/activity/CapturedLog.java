package com.slayclan.activity;

import java.util.List;
import lombok.Value;

@Value
public class CapturedLog
{
	List<OwnedItem> items;
	int gameCount;

	/** True when the capture holds exactly as many slots as the game itself counts as obtained. */
	public boolean isComplete()
	{
		return gameCount > 0 && items.size() == gameCount;
	}
}
