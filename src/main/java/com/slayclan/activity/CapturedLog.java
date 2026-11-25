package com.slayclan.activity;

import java.util.List;
import lombok.Value;

@Value
public class CapturedLog
{
	List<OwnedItem> items;
	int gameCount;

	public boolean isComplete()
	{
		return gameCount > 0 && items.size() == gameCount;
	}
}
