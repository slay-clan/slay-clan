package com.slayclan.trigger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Yama contracts are handed to Yama inside his instance and consumed when the fight starts. A contract that
 * disappears and is followed by a Yama kill counts as fulfilled, unless the player died or left the instance in
 * between. The Judge of Yama phase moves the player within the same instance, so it does not reset anything.
 * Contracts are never refunded on death, only some are dropped again after a win.
 */
@Slf4j
public class ContractTracker
{
	// 15 minutes, a safety net for contracts that leave the inventory without being used
	static final int MAX_FIGHT_TICKS = 1500;
	private static final int INSTANCE_ENTRY_TICKS = 5;
	private static final String CONTRACT_PREFIX = "Contract of ";
	private static final String YAMA_KILL = Triggers.kill("Yama");

	private Set<String> heldContracts;
	private String consumedContract;
	private int consumedTick;

	public void onInventory(Set<String> itemNames, int tick)
	{
		Set<String> contracts = new HashSet<>();
		for (String name : itemNames)
		{
			if (name.startsWith(CONTRACT_PREFIX))
			{
				contracts.add(name);
			}
		}
		if (heldContracts != null)
		{
			for (String held : heldContracts)
			{
				if (!contracts.contains(held))
				{
					log.info("{} left the inventory, counting the next Yama kill within {} ticks", held, MAX_FIGHT_TICKS);
					consumedContract = held;
					consumedTick = tick;
				}
			}
		}
		heldContracts = contracts;
	}

	/**
	 * Called every tick. A short grace period after the contract is used covers any move into the instance.
	 */
	public void onInstanceState(boolean inInstance, int tick)
	{
		if (consumedContract != null && !inInstance && tick - consumedTick >= INSTANCE_ENTRY_TICKS)
		{
			log.info("Forgetting {}: left the instance", consumedContract);
			consumedContract = null;
		}
	}

	public void onDeath()
	{
		if (consumedContract != null)
		{
			log.info("Forgetting {}: player died", consumedContract);
		}
		consumedContract = null;
	}

	public Optional<String> onKill(String killTrigger, int tick)
	{
		if (consumedContract == null || !YAMA_KILL.equals(killTrigger) || tick - consumedTick > MAX_FIGHT_TICKS)
		{
			return Optional.empty();
		}
		String contract = consumedContract;
		consumedContract = null;
		log.info("{} fulfilled", contract);
		return Optional.of(Triggers.contract(contract));
	}
}
