package com.slayclan.trigger;

import java.util.OptionalDouble;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class TimeParserTest
{
	@Test
	public void parsesMinutesAndSeconds()
	{
		assertEquals(OptionalDouble.of(75), TimeParser.parseSeconds("1:15"));
	}

	@Test
	public void parsesTenths()
	{
		assertEquals(OptionalDouble.of(68.4), TimeParser.parseSeconds("1:08.4"));
	}

	@Test
	public void parsesHundredths()
	{
		assertEquals(OptionalDouble.of(2202.6), TimeParser.parseSeconds("36:42.60"));
	}

	@Test
	public void parsesHours()
	{
		assertEquals(OptionalDouble.of(3723), TimeParser.parseSeconds("1:02:03"));
	}

	@Test
	public void rejectsGarbage()
	{
		assertFalse(TimeParser.parseSeconds("soon").isPresent());
		assertFalse(TimeParser.parseSeconds(null).isPresent());
	}
}
