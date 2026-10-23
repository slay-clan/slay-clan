package com.slayclan.clan;

import java.util.List;
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

	private static boolean isMember(ClanSettings clan, String name)
	{
		return clan.findMember(normalize(name)) != null;
	}

	private static String normalize(String name)
	{
		return name == null ? "" : name.replace(' ', ' ').trim();
	}
}
