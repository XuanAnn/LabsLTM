package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.Map;

import javax.swing.*;

public final class WebClient {
	public record Response(String status, Map<String, String> headers, byte[] body) {}

	public static Response request(String url, String method, String text) throws Exception {
		return request(url, method, text, Map.of());
	}

	public static Response request(
			String url, String method, String text, Map<String, String> extra) throws Exception {
		URI uri = URI.create(url);
		if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			throw new IOException("Only http:// URLs are supported");
		}
		int port = uri.getPort() < 0 ? 80 : uri.getPort();
		try (Socket socket = Wire.connect(uri.getHost(), port)) {
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			String path = uri.getRawPath();
			if (path == null || path.isEmpty()) {
				path = "/";
			}
			if (uri.getRawQuery() != null) {
				path += "?" + uri.getRawQuery();
			}
			Wire.line(out, method + " " + path + " HTTP/1.1");
			Wire.line(out, "Host: " + uri.getRawAuthority());
			Wire.line(out, "Connection: close");
			for (Map.Entry<String, String> header : extra.entrySet()) {
				if (!header.getKey().matches("[A-Za-z0-9-]+")
						|| header.getValue().contains("\r")
						|| header.getValue().contains("\n")) {
					throw new IOException("Invalid header");
				}
				Wire.line(out, header.getKey() + ": " + header.getValue());
			}
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			if (method.equals("POST")) {
				Wire.line(out, "Content-Length: " + bytes.length);
				Wire.line(out, "Content-Type: text/plain; charset=utf-8");
			}
			Wire.line(out, "");
			if (method.equals("POST")) {
				out.write(bytes);
				out.flush();
			}
			String status = Wire.line(in);
			if (status == null || !status.startsWith("HTTP/1.")) {
				throw new IOException("Invalid HTTP status");
			}
			Map<String, String> headers2 = new LinkedHashMap<>();
			for (int count = 0; ; count++) {
				String row = Wire.line(in);
				if (row == null) {
					throw new EOFException();
				}
				if (row.isEmpty()) {
					break;
				}
				if (count > 64 || !row.contains(":")) {
					throw new IOException("Invalid header");
				}
				int i = row.indexOf(':');
				headers2.put(
						row.substring(0, i).toLowerCase(Locale.ROOT), row.substring(i + 1).trim());
			}
			if (method.equals("HEAD") || status.matches("HTTP/1\\.[01] (204|304).*")) {
				return new Response(status, headers2, new byte[0]);
			}
			byte[] body;
			if ("chunked".equalsIgnoreCase(headers2.get("transfer-encoding"))) {
				ByteArrayOutputStream result = new ByteArrayOutputStream();
				while (true) {
					String size = Wire.line(in);
					if (size == null) {
						throw new EOFException();
					}
					int len = Integer.parseInt(size.split(";", 2)[0], 16);
					if (len == 0) {
						while (true) {
							String trailer = Wire.line(in);
							if (trailer == null) {
								throw new EOFException();
							}
							if (trailer.isEmpty()) {
								break;
							}
						}
						break;
					}
					if (result.size() + (long) len > 32 * 1024 * 1024) {
						throw new IOException("Download limit 32 MiB");
					}
					result.write(Wire.bytes(in, len));
					if (!"".equals(Wire.line(in))) {
						throw new IOException("Invalid chunk terminator");
					}
				}
				body = result.toByteArray();
			} else if (headers2.containsKey("content-length")) {
				body = Wire.bytes(in, Integer.parseInt(headers2.get("content-length")));
			} else {
				body = in.readNBytes(32 * 1024 * 1024 + 1);
				if (body.length > 32 * 1024 * 1024) {
					throw new IOException("Download limit 32 MiB");
				}
			}
			return new Response(status, headers2, body);
		}
	}

	public static void main(String[] args) throws Exception {
		Db db = new Db();
		SwingUtilities.invokeLater(
				() -> {
					Desk ui = new Desk("Lab 4 • HTTP client — GET / HEAD / POST");
					ui.field("URL", "http://localhost:8080/");
					ui.field("POST body", "Xin chào HTTP");
					ui.field("Save as", "downloads/result.bin");
					for (String method : List.of("GET", "HEAD", "POST")) {
						ui.button(
								method,
								() -> {
									Response response =
											request(ui.get("URL"), method, ui.get("POST body"));
									if (SaveOptions.enabled(SaveOptions.Kind.HISTORY)) {
										db.exec(
												"INSERT INTO history(url,method,status)"
														+ " VALUES(?,?,?)",
												ui.get("URL"),
												method,
												response.status());
									}
									return response.status()
											+ "\n"
											+ response.headers()
											+ "\n\n"
											+ new String(response.body(), StandardCharsets.UTF_8);
								});
					}
					ui.button(
							"Download",
							() -> {
								Response response2 = request(ui.get("URL"), "GET", "");
								if (!response2.status().contains(" 200 ")) {
									throw new IOException(response2.status());
								}
								Path filePath = Path.of(ui.get("Save as"));
								Files.createDirectories(filePath.toAbsolutePath().getParent());
								Files.write(filePath, response2.body());
								if (SaveOptions.enabled(SaveOptions.Kind.HISTORY)) {
									db.exec(
											"INSERT INTO history(url,method,status) VALUES(?,?,?)",
											ui.get("URL"),
											"DOWNLOAD",
											response2.status());
								}
								return "Saved " + response2.body().length + " bytes to " + filePath;
							});
					ui.button(
							"History",
							() -> {
								StringBuilder textBuilder = new StringBuilder();
								try (Connection connection = db.open();
										Statement statement = connection.createStatement();
										ResultSet resultSet =
												statement.executeQuery(
														"SELECT at,method,url,status FROM history"
																+ " ORDER BY id DESC LIMIT 100")) {
									while (resultSet.next()) {
										textBuilder
												.append(resultSet.getString(1))
												.append("  ")
												.append(resultSet.getString(2))
												.append(" ")
												.append(resultSet.getString(3))
												.append(" ")
												.append(resultSet.getString(4))
												.append('\n');
									}
								}
								return textBuilder.toString();
							});
					ui.show();
				});
	}
}
