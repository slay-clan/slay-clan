package com.slayclan.drop;

import java.util.List;
import lombok.Value;

@Value
public class DropJudgement
{
	List<ValuableDrop> drops;
	List<SkippedDrop> skipped;
}
