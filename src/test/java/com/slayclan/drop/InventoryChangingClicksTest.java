package com.slayclan.drop;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class InventoryChangingClicksTest
{
	private static final int NO_WIDGET = -1;

	@Test
	public void walkingTowardsTheContainerBringsNoItemsIn()
	{
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.WALK, NO_WIDGET, "Walk here"));
	}

	@Test
	public void cancellingAMenuOrExaminingBringsNoItemsIn()
	{
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.CANCEL, NO_WIDGET, "Cancel"));
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.EXAMINE_OBJECT, NO_WIDGET, "Examine"));
	}

	@Test
	public void switchingAPrayerOrTheRunOrbBringsNoItemsIn()
	{
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.PRAYERBOOK), "Activate"));
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.ORBS), "Toggle Run"));
	}

	@Test
	public void openingASideTabBringsNoItemsIn()
	{
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.TOPLEVEL_OSRS_STRETCH), "Inventory"));
	}

	@Test
	public void eatingOrDrinkingFromTheInventoryBringsNoItemsIn()
	{
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.INVENTORY), "Eat"));
		assertFalse(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.INVENTORY), "Drink"));
	}

	@Test
	public void wieldingFromTheInventoryCanBringTheOldWeaponIn()
	{
		assertTrue(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.INVENTORY), "Wield"));
	}

	@Test
	public void takingAGroundItemCanBringItIn()
	{
		assertTrue(InventoryChangingClicks.canBringItemsIn(MenuAction.GROUND_ITEM_THIRD_OPTION, NO_WIDGET, "Take"));
	}

	@Test
	public void talkingOrTradingWithAnNpcCanBringItemsIn()
	{
		assertTrue(InventoryChangingClicks.canBringItemsIn(MenuAction.NPC_THIRD_OPTION, NO_WIDGET, "Trade"));
	}

	@Test
	public void openingAnotherObjectCanBringItemsIn()
	{
		assertTrue(InventoryChangingClicks.canBringItemsIn(MenuAction.GAME_OBJECT_SECOND_OPTION, NO_WIDGET, "Bank"));
	}

	@Test
	public void withdrawingFromABankOrShopInterfaceCanBringItemsIn()
	{
		assertTrue(InventoryChangingClicks.canBringItemsIn(MenuAction.CC_OP, component(InterfaceID.BANKMAIN), "Withdraw-1"));
	}

	private static int component(int interfaceId)
	{
		return interfaceId << 16;
	}
}
