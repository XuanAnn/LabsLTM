package client;

import java.net.URI;

/** Thanh địa chỉ ẩn cổng; kết nối vẫn dùng URL đầy đủ. */
public final class BrowserAddress {
	private BrowserAddress() {}

	public static String display(String url) {
		URI address = URI.create(url);
		String authority = address.getRawAuthority();
		if (authority == null || address.getPort() < 0) {
			return url;
		}
		int start = url.indexOf(authority, url.indexOf(":") + 1);
		String visibleAuthority = authority.replaceFirst(":\\d+$", "");
		return url.substring(0, start) + visibleAuthority + url.substring(start + authority.length());
	}

	public static String resolve(String input, String actualUrl) {
		String text = input.trim();
		URI actual = URI.create(actualUrl);
		if (text.startsWith("/") || text.startsWith("?") || text.startsWith("#")) {
			return actual.resolve(text).toString();
		}
		if (!text.contains("://")) {
			text = "http://" + text;
		}
		URI target = URI.create(text);
		// Khi nhập lại địa chỉ đang hiển thị, khôi phục cổng đã ẩn của server này.
		if (target.getPort() < 0 && actual.getPort() >= 0
				&& actual.getHost() != null && actual.getHost().equalsIgnoreCase(target.getHost())
				&& actual.getScheme().equalsIgnoreCase(target.getScheme())) {
			String authority = target.getRawAuthority();
			int start = text.indexOf(authority, text.indexOf(":") + 1);
			return text.substring(0, start) + authority + ":" + actual.getPort()
					+ text.substring(start + authority.length());
		}
		return text;
	}
}
