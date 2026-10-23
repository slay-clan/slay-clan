package com.slayclan.trigger;

import java.util.Optional;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class ContractTrackerTest
{
	private final ContractTracker tracker = new ContractTracker();

	@Test
	public void reportsContractConsumedBeforeYamaKill()
	{
		tracker.onInventory(Set.of("Contract of Oathplate Acquisition", "Shark"), 100);
		tracker.onInventory(Set.of("Shark"), 110);

		assertEquals(Optional.of("CONTRACT:Contract of Oathplate Acquisition"), tracker.onKill("KILL:Yama", 400));
	}

	@Test
	public void reportsContractOnlyOnce()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);
		tracker.onKill("KILL:Yama", 400);

		assertFalse(tracker.onKill("KILL:Yama", 900).isPresent());
	}

	@Test
	public void ignoresKillOfOtherBoss()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);

		assertFalse(tracker.onKill("KILL:Zulrah", 200).isPresent());
	}

	@Test
	public void ignoresYamaKillLongAfterContractWasUsed()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);

		assertFalse(tracker.onKill("KILL:Yama", 110 + ContractTracker.MAX_FIGHT_TICKS + 1).isPresent());
	}

	@Test
	public void countsLongFightWithinLimit()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);

		assertEquals(Optional.of("CONTRACT:Contract of Shard Acquisition"), tracker.onKill("KILL:Yama", 110 + 1400));
	}

	@Test
	public void forgetsContractWhenPlayerDies()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);
		tracker.onDeath();

		assertFalse(tracker.onKill("KILL:Yama", 300).isPresent());
	}

	@Test
	public void keepsContractWhileInsideTheInstance()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);
		tracker.onInstanceState(true, 116);
		tracker.onInstanceState(true, 200);

		assertEquals(Optional.of("CONTRACT:Contract of Shard Acquisition"), tracker.onKill("KILL:Yama", 300));
	}

	@Test
	public void forgetsContractWhenLeavingTheInstance()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);
		tracker.onInstanceState(true, 116);
		tracker.onInstanceState(false, 150);
		tracker.onInstanceState(true, 151);

		assertFalse(tracker.onKill("KILL:Yama", 300).isPresent());
	}

	@Test
	public void allowsMovingIntoTheInstanceRightAfterUsingTheContract()
	{
		tracker.onInventory(Set.of("Contract of Shard Acquisition"), 100);
		tracker.onInventory(Set.of(), 110);
		tracker.onInstanceState(false, 111);
		tracker.onInstanceState(true, 113);

		assertEquals(Optional.of("CONTRACT:Contract of Shard Acquisition"), tracker.onKill("KILL:Yama", 300));
	}

	@Test
	public void ignoresYamaKillWithoutContract()
	{
		tracker.onInventory(Set.of("Shark"), 100);

		assertFalse(tracker.onKill("KILL:Yama", 200).isPresent());
	}

	@Test
	public void firstInventorySnapshotDoesNotCountAsConsumption()
	{
		tracker.onInventory(Set.of(), 100);

		assertFalse(tracker.onKill("KILL:Yama", 200).isPresent());
	}
}
