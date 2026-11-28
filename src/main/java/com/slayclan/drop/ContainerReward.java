package com.slayclan.drop;

import java.util.List;
import lombok.Value;
import net.runelite.api.Item;

@Value
public class ContainerReward
{
	String container;
	List<Item> gained;
}
