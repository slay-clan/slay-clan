package com.slayclan.activity;

import com.slayclan.task.Names;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;

/**
 * Item name to id for every item in the cache, built a slice per game tick on the client thread. RuneLite's own
 * item search only knows tradeable items, so collection log items such as pets and notes need this.
 */
public class ItemNameIndex
{
	static final int ITEMS_PER_TICK = 2000;

	private final Client client;
	private final Map<String, Integer> idsByName = new HashMap<>();
	private int nextId;
	private boolean complete;

	public ItemNameIndex(Client client)
	{
		this.client = client;
	}

	public void onTick()
	{
		if (complete)
		{
			return;
		}
		int count = client.getItemCount();
		int end = Math.min(count, nextId + ITEMS_PER_TICK);
		for (int id = nextId; id < end; id++)
		{
			remember(id, client.getItemDefinition(id));
		}
		nextId = end;
		complete = nextId >= count && count > 0;
	}

	public OptionalInt idOf(String name)
	{
		Integer id = idsByName.get(key(name));
		return id == null ? OptionalInt.empty() : OptionalInt.of(id);
	}

	// Noted and placeholder variants share the real item's name; the first real id is the one the log shows
	private void remember(int id, ItemComposition item)
	{
		if (item == null || item.getName() == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1)
		{
			return;
		}
		idsByName.putIfAbsent(key(item.getName()), id);
	}

	private static String key(String name)
	{
		return Names.normalize(name);
	}
}
