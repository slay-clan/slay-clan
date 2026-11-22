package com.slayclan.clan;

import java.util.List;
import lombok.Value;

@Value
public class ClanTeam
{
	List<String> clanMembers;
	boolean wholeTeamInClan;
}
