package com.slayclan.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What a PB post asserts about its time. The game keeps one personal best per boss across team sizes, so the plugin
 * tracks the fastest kill per scale itself and reports such a kill without calling it a record.
 */
@Getter
@RequiredArgsConstructor
public enum PersonalBestClaim
{
	NEW_RECORD(true),
	FASTEST_AT_SCALE(false),
	CURRENT(false);

	private final boolean newRecord;
}
