package com.slayclan.task;

import com.slayclan.config.TaskDefinition;
import lombok.Value;

/**
 * A task the game's own records satisfy, with the record that does it in words staff can check, such as the
 * combat achievement's name or the stored time.
 */
@Value
public class SatisfiedTask
{
	TaskDefinition task;
	String provenBy;
}
