package com.slayclan.trigger;

import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class DuplicatePetDetectorTest
{
	private final DuplicatePetDetector detector = new DuplicatePetDetector();

	@Test
	public void detectsDuplicatePetMessage()
	{
		assertEquals(Optional.of(Triggers.PET_DUPLICATE),
			detector.detect("<col=ef1020>You have a funny feeling like you would have been followed...</col>"));
	}

	@Test
	public void ignoresNewPetMessage()
	{
		assertFalse(detector.detect("You have a funny feeling like you're being followed.").isPresent());
	}

	@Test
	public void ignoresNewPetInBackpackMessage()
	{
		assertFalse(detector.detect("You feel something weird sneaking into your backpack.").isPresent());
	}

	@Test
	public void ignoresNullMessage()
	{
		assertFalse(detector.detect(null).isPresent());
	}
}
