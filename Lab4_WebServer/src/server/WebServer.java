package server;

import client.*;
import server.*;
import common.*;

import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.Map;
import java.util.stream.Stream;

/** A deliberately small HTTP/1.1 origin server, one request per connection. */
public final class WebServer implements AutoCloseable {
	private final Path root;
	private final Db db;
	private volatile ServerSocket listener;
	private final Set<Socket> clients = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final WebSessions sessions;
	private final java.util.concurrent.ScheduledExecutorService housekeeping =
			java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
					Thread.ofVirtual().factory());

	private static final class Trace {
		String method = "", target = "", action = "GET", sessionId;
		int status = 400;
		long bytes;
	}

	private static final ThreadLocal<Trace> trace = new ThreadLocal<>();

	public WebServer(Path root) throws Exception {
		this.root = root.toAbsolutePath().normalize();
		Files.createDirectories(this.root);
		db = new Db();
		sessions = new WebSessions(db);
	}

	public void serve(int port) throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			listener = serverSocket;
			housekeeping.scheduleWithFixedDelay(
					() -> {
						try {
							sessions.expire();
						} catch (Exception exception) {
							System.err.println("Sessions: " + exception.getMessage());
						}
					},
					2,
					2,
					java.util.concurrent.TimeUnit.SECONDS);
			System.out.println("HTTP :" + serverSocket.getLocalPort() + " root=" + root);
			while (!serverSocket.isClosed()) {
				Socket socket = serverSocket.accept();
				clients.add(socket);
				Thread.startVirtualThread(() -> handle(socket));
			}
		}
	}

	private void handle(Socket socket) {
		Trace event = new Trace();
		trace.set(event);
		try (socket) {
			socket.setSoTimeout(15000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			String first = Wire.line(in);
			if (first == null) {
				return;
			}
			String[] parts = first.split(" ");
			if (parts.length >= 2) {
				event.method = parts[0];
				event.target = parts[1];
			}
			if (parts.length != 3 || !parts[2].equals("HTTP/1.1")) {
				text(out, 400, "Bad request", false);
				return;
			}
			boolean head = parts[0].equals("HEAD");
			Map<String, String> headers = new HashMap<>();
			for (int count = 0; ; count++) {
				String line = Wire.line(in);
				if (line == null) {
					return;
				}
				if (line.isEmpty()) {
					break;
				}
				if (count >= 64 || !line.contains(":")) {
					text(out, 400, "Invalid headers", head);
					return;
				}
				int at = line.indexOf(':');
				String key = line.substring(0, at).toLowerCase(Locale.ROOT);
				if (headers.putIfAbsent(key, line.substring(at + 1).trim()) != null) {
					text(out, 400, "Duplicate headers", head);
					return;
				}
			}
			if (!headers.containsKey("host") || headers.containsKey("transfer-encoding")) {
				text(out, 400, "Host required; transfer encoding unsupported", head);
				return;
			}
			if (headers.containsKey("expect")) {
				text(out, 417, "Expectation unsupported", head);
				return;
			}
			if (!Set.of("GET", "HEAD", "POST").contains(parts[0])) {
				response(
						out,
						405,
						"text/plain",
						Map.of("Allow", "GET, HEAD, POST"),
						"Method not allowed".getBytes(StandardCharsets.UTF_8),
						head);
				return;
			}
			String path;
			try {
				path =
						URLDecoder.decode(
								parts[1].split("\\?", 2)[0].replace("+", "%2B"),
								StandardCharsets.UTF_8);
			} catch (IllegalArgumentException exception) {
				text(out, 400, "Invalid URL", head);
				return;
			}
			if (!path.startsWith("/")) {
				text(out, 400, "Invalid target", head);
				return;
			}
			event.action = parts[0];
			boolean sessionEndpoint = path.startsWith("/api/session/");
			boolean page =
					Set.of(
									"/",
									"/index.html",
									"/music",
									"/videos",
									"/documents",
									"/photos",
									"/downloads")
							.contains(path);
			event.sessionId =
					sessions.resolve(
							WebSessions.token(headers),
							socket.getInetAddress().getHostAddress(),
							path.equals("/api/session/open") || (page && parts[0].equals("GET")));
			if (parts[0].equals("POST")) {
				if (!path.equals("/api/echo") && !sessionEndpoint) {
					text(out, 404, "No endpoint", false);
					return;
				}
				int len;
				try {
					len = Integer.parseInt(headers.getOrDefault("content-length", "-1"));
				} catch (NumberFormatException exception2) {
					text(out, 400, "Invalid length", false);
					return;
				}
				if (len < 0) {
					text(out, 411, "Content-Length required", false);
					return;
				}
				if (len > 1048576) {
					text(out, 413, "Body too large", false);
					return;
				}
				byte[] body = Wire.bytes(in, len);
				if (sessionEndpoint) {
					event.action = "SESSION";
					if (!Set.of("/api/session/open", "/api/session/ping", "/api/session/close")
							.contains(path)) {
						text(out, 404, "No endpoint", false);
						return;
					}
					if (event.sessionId == null) {
						text(out, 409, "Session closed; reconnect", false);
						return;
					}
					if (path.endsWith("/close")) {
						sessions.end(event.sessionId);
					}
					response(
							out,
							200,
							"text/plain; charset=utf-8",
							Map.of(),
							event.sessionId.getBytes(StandardCharsets.UTF_8),
							false);
					return;
				}
				response(out, 200, "text/plain; charset=utf-8", Map.of(), body, false);
				return;
			}
			if (path.equals("/api/time")) {
				response(
						out,
						200,
						"application/json",
						Map.of(),
						("{\"time\":\"" + java.time.Instant.now() + "\"}")
								.getBytes(StandardCharsets.UTF_8),
						head);
				return;
			}
			if (path.equals("/api/library")) {
				response(
						out,
						200,
						"application/json; charset=utf-8",
						Map.of("Cache-Control", "no-store"),
						library().getBytes(StandardCharsets.UTF_8),
						head);
				return;
			}
			if (Set.of("/", "/music", "/videos", "/documents", "/photos", "/downloads")
					.contains(path)) {
				path = "/index.html";
			}
			Path file;
			try {
				file = Wire.inside(root, path.substring(1));
			} catch (Exception exception3) {
				text(out, 403, "Forbidden", head);
				return;
			}
			if (!Files.isRegularFile(file)) {
				text(out, 404, "Not found", head);
				return;
			}
			long total = Files.size(file), start = 0, end = total - 1;
			Map<String, String> extra = new HashMap<>();
			extra.put("Accept-Ranges", "bytes");
			int status = 200;
			if (isDownloadRequest(parts[1])) {
				event.action = "DOWNLOAD";
				extra.put(
						"Content-Disposition",
						"attachment; filename*=UTF-8''"
								+ URLEncoder.encode(
												file.getFileName().toString(),
												StandardCharsets.UTF_8)
										.replace("+", "%20"));
			}
			if (headers.containsKey("range")) {
				try {
					String text = headers.get("range");
					if (!text.matches("bytes=\\d*-\\d*")) {
						throw new IllegalArgumentException();
					}
					String[] parts2 = text.substring(6).split("-", -1);
					if (parts2[0].isEmpty()) {
						long suffix = Long.parseLong(parts2[1]);
						if (suffix <= 0) {
							throw new IllegalArgumentException();
						}
						start = Math.max(0, total - suffix);
					} else {
						start = Long.parseLong(parts2[0]);
						if (!parts2[1].isEmpty()) {
							end = Math.min(end, Long.parseLong(parts2[1]));
						}
					}
					if (start < 0 || start > end || start >= total) {
						throw new IllegalArgumentException();
					}
					status = 206;
					extra.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
				} catch (Exception exception4) {
					response(
							out,
							416,
							"text/plain",
							Map.of("Content-Range", "bytes */" + total),
							new byte[0],
							head);
					return;
				}
			}
			long length = end - start + 1;
			headers(out, status, mime(file), extra, length);
			if (!head) {
				try (InputStream stream = Files.newInputStream(file)) {
					stream.skipNBytes(start);
					byte[] buf = new byte[16384];
					long remaining = length;
					while (remaining > 0) {
						int count2 = stream.read(buf, 0, (int) Math.min(buf.length, remaining));
						if (count2 < 0) {
							throw new EOFException();
						}
						out.write(buf, 0, count2);
						remaining -= count2;
					}
				}
			}
			out.flush();
		} catch (Exception exception5) {
			System.err.println("HTTP: " + exception5.getMessage());
		} finally {
			clients.remove(socket);
			trace.remove();
			if (!event.method.isBlank() && SaveOptions.enabled(SaveOptions.Kind.REQUEST_LOG)) {
				try {
					db.exec(
							"INSERT INTO"
								+ " requests(method,target,remote_ip,status,bytes,action,session_id)"
								+ " VALUES(?,?,?,?,?,?,?)",
							event.method,
							event.target,
							socket.getInetAddress().getHostAddress(),
							event.status,
							event.bytes,
							event.action,
							event.sessionId);
				} catch (Exception exception6) {
					System.err.println("Request log: " + exception6.getMessage());
				}
			}
		}
	}

	private static boolean isDownloadRequest(String target) {
		String[] addressParts = target.split("\\?", 2);
		if (addressParts.length < 2) {
			return false;
		}
		for (String parameter : addressParts[1].split("&")) {
			if (parameter.equals("download=1")) {
				return true;
			}
		}
		return false;
	}

	private String library() throws IOException {
		List<String> entries = new ArrayList<>();
		Properties credits = new Properties();
		Path metadata = root.resolve("media/credits.properties");
		if (Files.isRegularFile(metadata)) {
			try (Reader reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
				credits.load(reader);
			}
		}
		Map<String, Set<String>> types =
				Map.of(
						"music",
						Set.of("mp3", "wav", "ogg", "m4a"),
						"videos",
						Set.of("mp4", "webm", "ogv"),
						"documents",
						Set.of("pdf"),
						"photos",
						Set.of("png", "jpg", "jpeg", "gif", "webp", "svg"));
		for (String category : List.of("music", "videos", "documents", "photos", "downloads")) {
			Path folder = root.resolve("media").resolve(category);
			if (!Files.isDirectory(folder)) {
				continue;
			}
			try (Stream<Path> files = Files.list(folder)) {
				for (Path file : files.sorted().toList()) {
					String name = file.getFileName().toString();
					int dot = name.lastIndexOf('.');
					if (!Files.isRegularFile(file)
							|| (!category.equals("downloads")
									&& (dot < 0
											|| !types.get(category)
													.contains(
															name.substring(dot + 1)
																	.toLowerCase(Locale.ROOT))))) {
						continue;
					}
					try {
						Wire.inside(root, "media/" + category + "/" + name);
					} catch (Exception exception) {
						continue;
					}
					String url =
							"/media/"
									+ category
									+ "/"
									+ URLEncoder.encode(name, StandardCharsets.UTF_8)
											.replace("+", "%20");
					String key = category + "/" + name;
					StringBuilder entry =
							new StringBuilder(
									"{\"category\":"
											+ json(category)
											+ ",\"name\":"
											+ json(name)
											+ ",\"url\":"
											+ json(url)
											+ ",\"size\":"
											+ Files.size(file));
					for (String field : List.of("title", "credit", "source", "license")) {
						String value = credits.getProperty(key + "." + field);
						if (value != null) {
							entry.append(",").append(json(field)).append(":").append(json(value));
						}
					}
					entries.add(entry.append("}").toString());
				}
			}
		}
		return "[" + String.join(",", entries) + "]";
	}

	private static String json(String value) {
		StringBuilder result = new StringBuilder("\"");
		for (char count : value.toCharArray()) {
			switch (count) {
				case '"' -> result.append("\\\"");
				case '\\' -> result.append("\\\\");
				default -> {
					if (count < 32) {
						result.append(String.format("\\u%04x", (int) count));
					} else {
						result.append(count);
					}
				}
			}
		}
		return result.append('"').toString();
	}

	private static String mime(Path filePath) throws IOException {
		String name = filePath.toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".mp4")) {
			return "video/mp4";
		}
		if (name.endsWith(".mp3")) {
			return "audio/mpeg";
		}
		if (name.endsWith(".wav")) {
			return "audio/wav";
		}
		if (name.endsWith(".ogg")) {
			return "audio/ogg";
		}
		if (name.endsWith(".m4a")) {
			return "audio/mp4";
		}
		if (name.endsWith(".webm")) {
			return "video/webm";
		}
		if (name.endsWith(".ogv")) {
			return "video/ogg";
		}
		if (name.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (name.endsWith(".js")) {
			return "text/javascript; charset=utf-8";
		}
		if (name.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		if (name.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (name.endsWith(".html")) {
			return "text/html; charset=utf-8";
		}
		if (name.endsWith(".txt")) {
			return "text/plain; charset=utf-8";
		}
		String text = Files.probeContentType(filePath);
		return text == null ? "application/octet-stream" : text;
	}

	private static void text(OutputStream out, int code, String text, boolean head)
			throws IOException {
		response(
				out,
				code,
				"text/plain; charset=utf-8",
				Map.of(),
				text.getBytes(StandardCharsets.UTF_8),
				head);
	}

	private static void response(
			OutputStream out,
			int code,
			String type,
			Map<String, String> headers,
			byte[] bytes2,
			boolean head)
			throws IOException {
		headers(out, code, type, headers, bytes2.length);
		if (!head) {
			out.write(bytes2);
		}
		out.flush();
	}

	private static void headers(
			OutputStream out, int code, String type, Map<String, String> headers, long length)
			throws IOException {
		Trace event = trace.get();
		if (event != null) {
			event.status = code;
			event.bytes = event.method.equals("HEAD") ? 0 : length;
		}
		String reason =
				switch (code) {
					case 200 -> "OK";
					case 206 -> "Partial Content";
					case 400 -> "Bad Request";
					case 403 -> "Forbidden";
					case 404 -> "Not Found";
					case 405 -> "Method Not Allowed";
					case 409 -> "Conflict";
					case 411 -> "Length Required";
					case 413 -> "Content Too Large";
					case 416 -> "Range Not Satisfiable";
					case 417 -> "Expectation Failed";
					default -> "Error";
				};
		Wire.line(out, "HTTP/1.1 " + code + " " + reason);
		Wire.line(out, "Content-Type: " + type);
		Wire.line(out, "Content-Length: " + length);
		Wire.line(out, "Connection: close");
		Wire.line(out, "X-Content-Type-Options: nosniff");
		if (event != null && event.sessionId != null) {
			Wire.line(out, "X-Lab-Session: " + event.sessionId);
			Wire.line(out, "Set-Cookie: LTM_SESSION=" + event.sessionId + "; Path=/; SameSite=Lax");
		}
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			Wire.line(out, entry.getKey() + ": " + entry.getValue());
		}
		Wire.line(out, "");
	}

	public static void main(String[] arguments) throws Exception {
		new WebServer(arguments.length > 1 ? Path.of(arguments[1]) : WebPaths.documentRoot())
				.serve(Integer.parseInt(Wire.arg(arguments, 0, "8080")));
	}

	public void close() throws IOException {
		housekeeping.shutdownNow();
		if (listener != null) {
			listener.close();
		}
		for (Socket socket : clients) {
			socket.close();
		}
		try {
			sessions.close();
		} catch (Exception exception) {
			throw new IOException("Cannot close sessions", exception);
		}
	}
}
