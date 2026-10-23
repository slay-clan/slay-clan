package com.slayclan.config;

import java.util.List;
import lombok.Data;

@Data
public class TaskDefinition
{
	private String id;
	private String name;
	private String trigger;
	private String tier;
	private int points;
	private int minPlayers = 1;
	private long minValue;
	private long valuePerPoint;
	private boolean includeStackable;
	private boolean once;
	private boolean clanOnly;
	private List<String> items;
	private int required;
	private List<String> sources;
	private String maxTime;
	private String timeType;
	private int teamSize;
	private int minLevel;
	private long minXp;
	private int minCount;
	private int minKillCount;
}
