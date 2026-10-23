package com.slayclan.activity;

import lombok.Value;

/**
 * A personal best RuneLite's own Chat Commands plugin has stored for this account. The boss is in that plugin's
 * lowercase form, such as "zulrah" or "chambers of xeric 5 players".
 */
@Value
public class StoredPersonalBest
{
	String boss;
	double seconds;
	String time;
}
