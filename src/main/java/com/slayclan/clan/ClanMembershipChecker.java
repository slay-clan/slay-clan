package com.slayclan.clan;

import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.clan.ClanSettings;

public class ClanMembershipChecker
{
	public boolean isClan(ClanSettings clan, String expectedClanName)
	{
		return clan != null && normalize(expectedClanName).equalsIgnoreCase(normalize(clan.getName()));
	}

	public boolean allMembers(ClanSettings clan, String expectedClanName, List<String> playerNames)
	{
		return isClan(clan, expectedClanName)
			&& playerNames.stream().allMatch(name -> isMember(clan, name));
	}

	/** The clan members among the names, in the same order; nobody else is ever named in a post. */
	public List<String> membersAmong(ClanSettings clan, String expectedClanName, List<String> playerNames)
	{
		if (!isClan(clan, expectedClanName))
		{
			return List.of();
		}
		return playerNames.stream().filter(name -> isMember(clan, name)).collect(Collectors.toList());
	}

	private static boolean isMember(ClanSettings clan, String name)
	{
		return clan.findMember(normalize(name)) != null;
	}

	private static String normalize(String name)
	{
		return name == null ? "" : name.replace(' ', ' ').trim();
	}
}
