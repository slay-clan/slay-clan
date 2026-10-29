package com.slayclan.config;

import java.util.regex.Pattern;
import okhttp3.HttpUrl;

/**
 * Where a request that carries a secret, the clan token or the config URL, may go: over TLS, or in plain
 * http only to this computer, where nothing can sit in between, which lets a local test bot and config server
 * work. Decided on the parsed URL, since a prefix check is fooled by userinfo such as
 * {@code http://localhost:@evil.example/}.
 */
public final class SecretSafeUrl
{
	private static final Pattern LOOPBACK_IPV4 = Pattern.compile("127(\\.\\d{1,3}){3}");

	private SecretSafeUrl()
	{
	}

	public static boolean accepts(String url)
	{
		HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
		return parsed != null && accepts(parsed);
	}

	public static boolean accepts(HttpUrl url)
	{
		return url.isHttps() || isThisComputer(url.host());
	}

	private static boolean isThisComputer(String host)
	{
		return "localhost".equals(host) || LOOPBACK_IPV4.matcher(host).matches() || "::1".equals(host);
	}
}
