package com.slayclan.trigger;

import lombok.Value;

@Value
public class PersonalBest
{
	TimedLine.Kind kind;
	double runSeconds;
	double bestSeconds;
	boolean newRecord;
}
