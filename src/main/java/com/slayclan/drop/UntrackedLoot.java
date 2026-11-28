package com.slayclan.drop;

import java.util.List;
import lombok.Value;

@Value
public class UntrackedLoot
{
	String source;
	List<NamedItem> items;
}
