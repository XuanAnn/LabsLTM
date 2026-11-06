package common;

import client.*;
import server.*;
import common.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map;
import java.util.regex.Matcher;

/** MIME text and delivery details used by both the server export and the client. */
public final class MailText {
	public record Content(Map<String, String> headers, String body, String raw) {
		public String field(String name) {
			return decodeHeader(headers.getOrDefault(name.toLowerCase(Locale.ROOT), ""));
		}
	}

	private MailText() {}

	public static Content parse(String original) {
		String raw = original.replace("\r\n", "\n");
		String[] sections = raw.split("\n\n", 2);
		Map<String, String> headers = new LinkedHashMap<>();
		String previous = null;
		for (String line : sections[0].split("\n")) {
			if ((line.startsWith(" ") || line.startsWith("\t")) && previous != null) {
				headers.put(previous, headers.get(previous) + " " + line.trim());
				continue;
			}
			int at = line.indexOf(':');
			if (at > 0) {
				previous = line.substring(0, at).toLowerCase(Locale.ROOT);
				headers.put(previous, line.substring(at + 1).trim());
			}
		}
		String body = sections.length > 1 ? sections[1] : "";
		if ("base64".equalsIgnoreCase(headers.get("content-transfer-encoding"))) {
			try {
				body = new String(Base64.getMimeDecoder().decode(body), StandardCharsets.UTF_8);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return new Content(Collections.unmodifiableMap(headers), body, raw);
	}

	public static String decodeHeader(String value) {
		Matcher matcher =
				java.util.regex.Pattern.compile(
								"=\\?UTF-8\\?B\\?([^?]+)\\?=",
								java.util.regex.Pattern.CASE_INSENSITIVE)
						.matcher(value);
		StringBuffer decoded = new StringBuffer();
		while (matcher.find()) {
			try {
				matcher.appendReplacement(
						decoded,
						java.util.regex.Matcher.quoteReplacement(
								new String(
										Base64.getDecoder().decode(matcher.group(1)),
										StandardCharsets.UTF_8)));
			} catch (IllegalArgumentException exception) {
				matcher.appendReplacement(
						decoded, java.util.regex.Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(decoded);
		return decoded.toString();
	}

	public static byte[] details(byte[] original, Map<String, String> details) {
		String text = new String(original, StandardCharsets.UTF_8).replace("\r\n", "\n");
		String[] sections = text.split("\n\n", 2);
		StringBuilder result = new StringBuilder();
		boolean skip = false;
		for (String row : sections[0].split("\n")) {
			if (row.startsWith(" ") || row.startsWith("\t")) {
				if (!skip) {
					result.append(row).append("\r\n");
				}
				continue;
			}
			skip = row.toLowerCase(Locale.ROOT).startsWith("x-local-");
			if (!skip) {
				result.append(row).append("\r\n");
			}
		}
		for (Map.Entry<String, String> entry : details.entrySet()) {
			String row =
					entry.getKey() + ": " + entry.getValue().replace('\r', ' ').replace('\n', ' ');
			while (row.length() > 900) {
				int at = row.lastIndexOf(' ', 900);
				if (at < 100) {
					at = 900;
				}
				result.append(row, 0, at).append("\r\n");
				row = " " + row.substring(at).stripLeading();
			}
			result.append(row).append("\r\n");
		}
		result.append("\r\n");
		if (sections.length > 1) {
			result.append(sections[1].replace("\n", "\r\n"));
		}
		if (!result.toString().endsWith("\r\n")) {
			result.append("\r\n");
		}
		return result.toString().getBytes(StandardCharsets.UTF_8);
	}

	public static String ipOnly(String peer) {
		if (peer == null || peer.isBlank()) {
			return "Chưa kết nối";
		}
		String ip = peer.replaceFirst("^/", "");
		if (ip.startsWith("[") && ip.contains("]")) {
			return ip.substring(1, ip.indexOf(']'));
		}
		return ip.matches("[^:]+:[0-9]+") ? ip.substring(0, ip.lastIndexOf(':')) : ip;
	}
}
