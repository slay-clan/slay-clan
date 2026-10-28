package com.slayclan.config;

import com.slayclan.task.Names;
import java.util.List;
import lombok.Data;

@Data
public class RemoteConfig
{
	private String clanName;
	private String apiUrl;
	private List<TaskDefinition> tasks;
	private List<String> ignoredContent;

	/**
	 * True when the clan has switched this boss or raid off: no task and no personal best is posted for it.
	 * Lets the clan silence new content whose messages turn out wrong, within minutes and without a plugin
	 * release. Names match like triggers, ignoring case and apostrophes, with a trailing {@code *} wildcard.
	 */
	public boolean ignores(String contentName)
	{
		return ignoredContent != null && ignoredContent.stream().anyMatch(pattern -> Names.matches(pattern, contentName));
	}
}
