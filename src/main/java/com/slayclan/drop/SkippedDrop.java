package com.slayclan.drop;

import lombok.Value;

@Value
public class SkippedDrop
{
	String itemName;
	long value;
	String reason;
}
