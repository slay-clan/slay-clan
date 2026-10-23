package com.slayclan.trigger;

import lombok.Value;

@Value
public class TimedLine
{
	public enum Kind
	{
		/** ToB and ToA total completion time, including time between rooms. */
		TOTAL,
		/** ToB completion time and ToA challenge completion time, excluding time between rooms. */
		CHALLENGE,
		/** The only time printed, as for bosses, the Inferno and Chambers of Xeric. */
		SINGLE
	}

	Kind kind;
	double seconds;
}
