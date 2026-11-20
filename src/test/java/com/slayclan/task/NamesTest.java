package com.slayclan.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class NamesTest
{
	@Test
	public void ignoresApostrophes()
	{
		assertTrue(Names.same("CA:It Wasn't a Fluke", "CA:It Wasnt a Fluke"));
	}

	@Test
	public void treatsTypographicApostropheLikeStraight()
	{
		assertTrue(Names.same("Ghommal’s hilt 3", "Ghommal's hilt 3"));
	}

	@Test
	public void ignoresCaseAndSurroundingSpaces()
	{
		assertTrue(Names.same(" pet penance queen ", "Pet Penance Queen"));
	}

	@Test
	public void keepsParenthesesSignificant()
	{
		assertFalse(Names.same("Victor's cape (100)", "Victor's cape (50)"));
	}

	@Test
	public void matchesPrefixPatternIgnoringApostrophes()
	{
		assertTrue(Names.matches("Pharaoh's*", "Pharaohs sceptre"));
	}

	@Test
	public void matchesPatternWithoutWildcardExactly()
	{
		assertFalse(Names.matches("Jar of dirt", "Jar of dirt (noted)"));
	}

	@Test
	public void handlesNull()
	{
		assertFalse(Names.same(null, "Fire cape"));
	}
}
