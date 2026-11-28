package com.slayclan.drop;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

/**
 * Whether a click could put items in the inventory, which would make them look like a container's reward. Anything
 * not known to be harmless counts, including wielding (the old weapon comes back) and opening items.
 */
public final class InventoryChangingClicks
{
	private static final Set<MenuAction> HARMLESS_ACTIONS = EnumSet.of(MenuAction.WALK, MenuAction.SET_HEADING,
		MenuAction.CANCEL, MenuAction.EXAMINE_OBJECT, MenuAction.EXAMINE_NPC, MenuAction.EXAMINE_ITEM_GROUND,
		MenuAction.EXAMINE_ITEM, MenuAction.EXAMINE_WORLD_ENTITY, MenuAction.RUNELITE_OVERLAY,
		MenuAction.RUNELITE_OVERLAY_CONFIG, MenuAction.RUNELITE_INFOBOX);
	private static final Set<MenuAction> WIDGET_ACTIONS = EnumSet.of(MenuAction.CC_OP, MenuAction.CC_OP_LOW_PRIORITY,
		MenuAction.WIDGET_TYPE_1, MenuAction.WIDGET_TYPE_4, MenuAction.WIDGET_TYPE_5);
	private static final Set<Integer> HARMLESS_INTERFACES = Set.of(InterfaceID.PRAYERBOOK, InterfaceID.QUICKPRAYER,
		InterfaceID.ORBS, InterfaceID.TOPLEVEL, InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.TOPLEVEL_PRE_EOC,
		InterfaceID.TOPLEVEL_OSM, InterfaceID.TOPLEVEL_DISPLAY);
	// Food and potions leave at most a pie half or a lower dose behind, never anything worth a drop post
	private static final Set<String> HARMLESS_INVENTORY_OPTIONS = Set.of("Eat", "Drink");

	private InventoryChangingClicks()
	{
	}

	public static boolean canBringItemsIn(MenuAction action, int widgetId, String option)
	{
		return !HARMLESS_ACTIONS.contains(action) && !harmlessWidgetClick(action, widgetId, option);
	}

	private static boolean harmlessWidgetClick(MenuAction action, int widgetId, String option)
	{
		return WIDGET_ACTIONS.contains(action) && harmlessWidget(WidgetUtil.componentToInterface(widgetId), option);
	}

	private static boolean harmlessWidget(int interfaceId, String option)
	{
		return HARMLESS_INTERFACES.contains(interfaceId) || eatingOrDrinking(interfaceId, option);
	}

	private static boolean eatingOrDrinking(int interfaceId, String option)
	{
		return interfaceId == InterfaceID.INVENTORY && HARMLESS_INVENTORY_OPTIONS.contains(option);
	}
}
