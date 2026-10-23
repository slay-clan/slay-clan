package com.slayclan.drop;

import lombok.Value;

@Value
public class PricedItem
{
	String name;
	int quantity;
	long unitPrice;
	boolean stackable;
}
