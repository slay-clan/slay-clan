package com.slayclan.trigger;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TimeFormatTest
{
	@Test
	public void printsWholeSecondsWithoutAFraction()
	{
		assertEquals("0:55", TimeFormat.of(55));
	}

	@Test
	public void printsHundredthsWhenPresent()
	{
		assertEquals("29:49.20", TimeFormat.of(1789.2));
	}

	@Test
	public void keepsMinutesAboveAnHour()
	{
		assertEquals("104:31", TimeFormat.of(6271));
	}
}
