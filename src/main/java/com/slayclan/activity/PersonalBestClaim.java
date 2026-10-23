package com.slayclan.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What a PB post asserts about its time. The game keeps one personal best per boss across team sizes, so the plugin
 * tracks the fastest kill per scale itself and says so when the game did not call it a record.
 */
@Getter
@RequiredArgsConstructor
public enum PersonalBestClaim
{
	NEW_RECORD("Has set a new personal best", true),
	FASTEST_AT_SCALE("Fastest kill at this scale since the plugin was installed", false),
	CURRENT("Current personal best", false);

	private final String description;
	private final boolean newRecord;
}
