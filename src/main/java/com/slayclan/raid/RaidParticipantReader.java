package com.slayclan.raid;

import com.slayclan.trigger.Triggers;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Raids read the party list, which includes dead or far away members: Chambers of Xeric from its raid side
 * panel, Theatre of Blood, Tombs of Amascut and the Fractured Archive from varclients. Everything else, and
 * any raid whose list is unavailable, falls back to the players currently in the scene.
 */
public class RaidParticipantReader
{
	private static final String THEATRE_OF_BLOOD = Triggers.RAID_PREFIX + "Theatre of Blood";
	private static final String CHAMBERS_OF_XERIC = Triggers.RAID_PREFIX + "Chambers of Xeric";
	private static final String TOMBS_OF_AMASCUT = Triggers.RAID_PREFIX + "Tombs of Amascut";
	private static final String FRACTURED_ARCHIVE = Triggers.RAID_PREFIX + "Fractured Archive";
	private static final int[] TOB_PARTY_NAMES = {
		VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME1, VarClientID.TOB_CLIENT_NAME2,
		VarClientID.TOB_CLIENT_NAME3, VarClientID.TOB_CLIENT_NAME4
	};
	private static final int[] TOA_PARTY_NAMES = {
		VarClientID.TOA_CLIENT_NAME0, VarClientID.TOA_CLIENT_NAME1, VarClientID.TOA_CLIENT_NAME2,
		VarClientID.TOA_CLIENT_NAME3, VarClientID.TOA_CLIENT_NAME4, VarClientID.TOA_CLIENT_NAME5,
		VarClientID.TOA_CLIENT_NAME6, VarClientID.TOA_CLIENT_NAME7
	};
	private static final int[] TFA_PARTY_NAMES = {
		VarClientID.TFA_CLIENT_NAME0, VarClientID.TFA_CLIENT_NAME1, VarClientID.TFA_CLIENT_NAME2,
		VarClientID.TFA_CLIENT_NAME3, VarClientID.TFA_CLIENT_NAME4, VarClientID.TFA_CLIENT_NAME5,
		VarClientID.TFA_CLIENT_NAME6, VarClientID.TFA_CLIENT_NAME7
	};
	// Slot 0 is the player themselves and has no varbit
	private static final int[] TFA_PARTY_SLOTS = {
		VarbitID.TFA_CLIENT_P1, VarbitID.TFA_CLIENT_P2, VarbitID.TFA_CLIENT_P3, VarbitID.TFA_CLIENT_P4,
		VarbitID.TFA_CLIENT_P5, VarbitID.TFA_CLIENT_P6, VarbitID.TFA_CLIENT_P7
	};

	private final Client client;

	@Inject
	public RaidParticipantReader(Client client)
	{
		this.client = client;
	}

	public List<String> read(String trigger)
	{
		List<String> party = raidPartyList(trigger);
		return party.isEmpty() ? scenePlayers() : party;
	}

	/**
	 * How many players the game says are in the party, or 0 when it does not say. The Fractured Archive keeps
	 * one varbit per party slot, which is set while a player fills it, so the count stays right even when the
	 * name list could not be read.
	 */
	public int gamesTeamSize(String trigger)
	{
		if (!trigger.startsWith(FRACTURED_ARCHIVE))
		{
			return 0;
		}
		return 1 + (int) Arrays.stream(TFA_PARTY_SLOTS).filter(slot -> client.getVarbitValue(slot) != 0).count();
	}

	private List<String> raidPartyList(String trigger)
	{
		if (trigger.startsWith(CHAMBERS_OF_XERIC))
		{
			return coxPartyNames();
		}
		if (trigger.startsWith(THEATRE_OF_BLOOD))
		{
			return partyNames(TOB_PARTY_NAMES);
		}
		if (trigger.startsWith(TOMBS_OF_AMASCUT))
		{
			return partyNames(TOA_PARTY_NAMES);
		}
		if (trigger.startsWith(FRACTURED_ARCHIVE))
		{
			return partyNames(TFA_PARTY_NAMES);
		}
		return Collections.emptyList();
	}

	private List<String> coxPartyNames()
	{
		Widget list = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
		Widget[] children = list == null ? null : list.getChildren();
		if (children == null)
		{
			return Collections.emptyList();
		}
		return Arrays.stream(children)
			.filter(Objects::nonNull)
			.map(Widget::getName)
			.filter(Objects::nonNull)
			.map(Text::removeTags)
			.map(Text::sanitize)
			.filter(name -> !name.trim().isEmpty())
			.collect(Collectors.toList());
	}

	private List<String> partyNames(int[] varClientIds)
	{
		return Arrays.stream(varClientIds)
			.mapToObj(client::getVarcStrValue)
			.filter(Objects::nonNull)
			.map(Text::removeTags)
			.map(Text::sanitize)
			.filter(name -> !name.trim().isEmpty())
			.collect(Collectors.toList());
	}

	private List<String> scenePlayers()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return Collections.emptyList();
		}
		return worldView.players().stream()
			.filter(Objects::nonNull)
			.map(Actor::getName)
			.filter(Objects::nonNull)
			.map(Text::sanitize)
			.collect(Collectors.toList());
	}
}
