import client.*;
import server.*;
import common.*;
import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Integration tests use real processes/sockets and isolated persistent databases. */
public class IntegrationTest {
	static final List<Process> processes = new ArrayList<>();
	static Path dir;
	static Path mysqlConfig;
	static int checks;

	static void check(boolean ok, String label) {
		if (!ok) throw new AssertionError(label);
		checks++;
		System.out.println("PASS " + label);
	}

	static int port() throws Exception {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}

	static void until(BooleanSupplier condition) throws Exception {
		long end = System.nanoTime() + 12_000_000_000L;
		while (System.nanoTime() < end) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(100);
		}
		throw new AssertionError("Timed out");
	}

	static Process start(String clazz, String... args) throws Exception {
		List<String> cmd =
				new ArrayList<>(
						List.of(
								Path.of(System.getProperty("java.home"), "bin", "java.exe")
										.toString(),
								"-Dfile.encoding=UTF-8",
								"-cp",
								System.getProperty("java.class.path"),
								clazz));
		cmd.addAll(List.of(args));
		ProcessBuilder pb =
				new ProcessBuilder(cmd)
						.redirectErrorStream(true)
						.redirectOutput(
								dir.resolve(clazz + "-" + processes.size() + ".log").toFile());
		pb.environment().put("LAB_DB_CONFIG", mysqlConfig.toString());
		Process p = pb.start();
		processes.add(p);
		return p;
	}

	static void ready(int port) throws Exception {
		until(
				() -> {
					try (Socket s = Wire.connect("127.0.0.1", port)) {
						return true;
					} catch (Exception e) {
						return false;
					}
				});
	}

	static void stop(Process p) throws Exception {
		p.destroy();
		if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
			p.destroyForcibly();
			p.waitFor();
		}
	}

	static String raw(int port, String request) throws Exception {
		try (Socket s = Wire.connect("127.0.0.1", port)) {
			s.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
			s.getOutputStream().flush();
			return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	static void web() throws Exception {
		int port = port();
		Path root = dir.resolve("public");
		Files.createDirectories(root);
		Files.writeString(root.resolve("index.html"), "<h1>Xin chào</h1>");
		byte[] binary = new byte[1000];
		new Random(4).nextBytes(binary);
		Files.write(root.resolve("clip.mp4"), binary);
		try (var paths = Files.walk(Path.of("public/media"))) {
			for (Path source : paths.filter(Files::isRegularFile).toList()) {
				Path dest =
						root.resolve("media").resolve(Path.of("public/media").relativize(source));
				Files.createDirectories(dest.getParent());
				Files.copy(source, dest);
			}
		}
		Files.writeString(root.resolve("media/music/Tiếng Việt & vui.mp3"), "test");
		start("server.WebServer", "" + port, root.toString());
		ready(port);
		String base = "http://localhost:" + port;
		var library = WebClient.request(base + "/api/library", "GET", "");
		String listing = new String(library.body(), StandardCharsets.UTF_8);
		check(
				library.status().contains("200")
						&& List.of("music", "videos", "documents", "photos").stream()
								.allMatch(c -> listing.contains("\"category\":\"" + c + "\"")),
				"Library lists all four services");
		check(
				listing.contains("Tiếng Việt & vui.mp3")
						&& listing.contains("Ti%E1%BA%BFng%20Vi%E1%BB%87t%20%26%20vui.mp3"),
				"Library preserves Unicode names and encodes URLs");
		for (String sample :
				List.of(
						"music/Moonlight-Sonata.ogg",
						"videos/Hoa-va-ong.webm",
						"documents/RFC-9110.pdf",
						"photos/Trai-dat-Apollo-17.jpg")) {
			var media = WebClient.request(base + "/media/" + sample, "GET", "");
			check(
					media.status().contains("200")
							&& Arrays.equals(
									media.body(),
									Files.readAllBytes(root.resolve("media/" + sample)))
							&& !media.headers()
									.get("content-type")
									.equals("application/octet-stream"),
					"Media exact download and MIME: " + sample);
		}
		check(
				WebClient.request(base + "/api/library", "HEAD", "").body().length == 0,
				"Library HEAD has no body");
		Files.writeString(root.resolve("media/music/New.mp3"), "new");
		check(
				new String(
								WebClient.request(base + "/api/library", "GET", "").body(),
								StandardCharsets.UTF_8)
						.contains("New.mp3"),
				"Library discovers new files without restart");
		check(
				listing.contains("\"category\":\"downloads\"")
						&& Arrays.equals(
								WebClient.request(
												base + "/media/downloads/Tai-lieu-mau.zip",
												"GET",
												"")
										.body(),
								Files.readAllBytes(
										root.resolve("media/downloads/Tai-lieu-mau.zip"))),
				"Downloads service lists and returns exact ZIP bytes");
		var get = WebClient.request(base + "/", "GET", "");
		check(
				get.status().contains("200")
						&& new String(get.body(), StandardCharsets.UTF_8).contains("Xin chào"),
				"HTTP GET Unicode");
		boolean routes = true;
		for (String route : List.of("music", "videos", "documents", "photos", "downloads")) {
			var page = WebClient.request(base + "/" + route, "GET", "");
			var metadata = WebClient.request(base + "/" + route, "HEAD", "");
			routes &=
					page.status().contains("200")
							&& Arrays.equals(page.body(), get.body())
							&& metadata.status().contains("200")
							&& metadata.body().length == 0;
		}
		check(routes, "Service URLs without fragments support direct GET and HEAD");
		var head = WebClient.request(base + "/clip.mp4", "HEAD", "");
		check(
				head.body().length == 0 && head.headers().get("content-length").equals("1000"),
				"HTTP HEAD has metadata and no body");
		var post = WebClient.request(base + "/api/echo", "POST", "Tiếng Việt");
		check(
				new String(post.body(), StandardCharsets.UTF_8).equals("Tiếng Việt"),
				"HTTP POST byte-based Content-Length");
		check(
				Arrays.equals(binary, WebClient.request(base + "/clip.mp4", "GET", "").body()),
				"HTTP binary download exact bytes");
		String range =
				raw(
						port,
						"GET /clip.mp4 HTTP/1.1\r\nHost: localhost\r\nRange: bytes=10-19\r\n\r\n");
		check(
				range.contains("206 Partial Content") && range.contains("Content-Length: 10\r\n"),
				"HTTP byte range for media");
		check(
				raw(port, "GET /%2e%2e/mail.db HTTP/1.1\r\nHost: localhost\r\n\r\n")
						.contains("403 Forbidden"),
				"HTTP rejects encoded traversal");
		check(
				raw(
								port,
								"POST /api/echo HTTP/1.1\r\n"
										+ "Host: localhost\r\n"
										+ "Content-Length: 1\r\n"
										+ "Content-Length: 2\r\n\r\n"
										+ "x")
						.contains("400 Bad Request"),
				"HTTP rejects ambiguous framing");
		check(
				WebClient.request(base + "/missing", "GET", "").status().contains("404"),
				"HTTP missing resource");
		ClientController controller = new ClientController();
		controller.connect("localhost", port);
		controller.request(base + "/api/echo", "POST", "Lịch sử MySQL");
		check(!controller.history().isEmpty(), "HTTP client history is persisted in MySQL");
		Db database = new Db(mysqlConfig);
		until(
				() -> {
					try {
						return database.count("requests") >= 8;
					} catch (Exception e) {
						return false;
					}
				});
		check(
				!database.rows("SELECT remote_ip,status FROM requests WHERE method='POST'")
						.isEmpty(),
				"HTTP server request IP and status are persisted in MySQL");
		var connected =
				database.rows(
						"SELECT id,remote_ip FROM sessions WHERE disconnected_at IS NULL AND"
								+ " protocol='HTTP' ORDER BY connected_at DESC");
		String id = controller.headersFor(base).get("X-Lab-Session");
		check(
				id != null
						&& !database.rows(
										"SELECT id FROM sessions WHERE id=? AND disconnected_at IS"
												+ " NULL AND remote_ip='127.0.0.1'",
										id)
								.isEmpty(),
				"Client connect creates a persistent server session with IP only");
		controller.request(base + "/downloads", "HEAD", "");
		controller.request(base + "/videos", "HEAD", "");
		until(
				() -> {
					try {
						return database.rows(
												"SELECT id FROM requests WHERE session_id=? AND"
														+ " method='HEAD'",
												id)
										.size()
								>= 3;
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "HEAD works repeatedly after the initial connection");
		check(
				new String(
								controller
										.request(base + "/api/echo", "POST", "Bài gửi thử lần hai")
										.body(),
								StandardCharsets.UTF_8)
						.equals("Bài gửi thử lần hai"),
				"Connected client POST sends and returns the actual body");
		Path downloaded = dir.resolve("client-download.zip");
		controller.download(base + "/media/downloads/Tai-lieu-mau.zip", downloaded);
		check(
				Arrays.equals(
						Files.readAllBytes(downloaded),
						Files.readAllBytes(root.resolve("media/downloads/Tai-lieu-mau.zip"))),
				"Connected client download saves exact file bytes");
		until(
				() -> {
					try {
						return !database.rows(
										"SELECT id FROM requests WHERE session_id=? AND"
												+ " method='GET' AND action='DOWNLOAD'",
										id)
								.isEmpty();
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "Server distinguishes DOWNLOAD while retaining the HTTP GET method");
		check(
				!database.rows("SELECT id FROM history WHERE method='DOWNLOAD'").isEmpty(),
				"Client history labels file download explicitly");
		controller.disconnect();
		until(
				() -> {
					try {
						return !database.rows(
										"SELECT id FROM sessions WHERE id=? AND disconnected_at IS"
												+ " NOT NULL",
										id)
								.isEmpty();
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "Client disconnect closes its server session");
		controller.connect("localhost", port);
		String next = controller.headersFor(base).get("X-Lab-Session");
		check(!next.equals(id), "Reconnect creates a different session");
		database.exec(
				"UPDATE sessions SET last_seen=DATE_SUB(NOW(),INTERVAL 60 SECOND) WHERE id=?",
				next);
		until(
				() -> {
					try {
						return !database.rows(
										"SELECT id FROM sessions WHERE id=? AND disconnected_at IS"
												+ " NOT NULL",
										next)
								.isEmpty();
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "Server expires a lost client without leaving it online");
		controller.disconnect();
	}

	static void defaultRootFromOtherDirectory() throws Exception {
		int serverPort = port();
		List<String> classpath = new ArrayList<>();
		for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
			classpath.add(Path.of(entry).toAbsolutePath().toString());
		}
		ProcessBuilder builder = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
				"-cp", String.join(java.io.File.pathSeparator, classpath),
				"server.WebServer", Integer.toString(serverPort));
		builder.directory(dir.toFile());
		builder.redirectErrorStream(true).redirectOutput(dir.resolve("default-root.log").toFile());
		builder.environment().put("LAB_DB_CONFIG", mysqlConfig.toString());
		Process server = builder.start();
		processes.add(server);
		try {
			ready(serverPort);
			String origin = "http://127.0.0.1:" + serverPort;
			byte[] expected = Files.readAllBytes(WebPaths.documentRoot().resolve("index.html"));
			WebClient.Response home = WebClient.request(origin + "/", "GET", "");
			check(home.status().contains(" 200 ") && Arrays.equals(expected, home.body()),
					"Default homepage loads when launched outside the lab directory");
			check(WebClient.request(origin + "/music", "GET", "").status().contains(" 200 "),
					"Service route loads with the automatically resolved public folder");
		} finally {
			stop(server);
		}
	}

	static void lifecycle() throws Exception {
		ServerController controller = new ServerController();
		int first = port(), second = port();
		var settings =
				new common.AdminFrame.Settings(
						mysqlConfig, first, second, "Local", "127.0.0.1", "");
		try {
			controller.start(settings);
			ready(first);
			check(controller.running(), "Server dashboard controller starts service");
			controller.stop();
			until(
					() -> {
						try (Socket ignored = Wire.connect("localhost", first)) {
							return false;
						} catch (Exception e) {
							return true;
						}
					});
			check(!controller.running(), "Server dashboard controller releases service port");
			controller.start(settings);
			ready(first);
			check(controller.running(), "Server dashboard controller restarts on the same port");
		} finally {
			controller.stop();
		}
	}

	public static void main(String[] args) throws Exception {
		Files.createDirectories(Path.of("target"));
		dir = Files.createTempDirectory(Path.of("target"), "integration-").toAbsolutePath();
		System.out.println("Artifacts: " + dir);
		mysqlConfig = TestDatabase.create(dir, "main");
		System.setProperty("lab.db.config", mysqlConfig.toString());
		try {
			web();
			lifecycle();
			defaultRootFromOtherDirectory();
			System.out.println("ALL " + checks + " CHECKS PASSED");
		} finally {
			for (Process p : processes) if (p.isAlive()) stop(p);
			TestDatabase.cleanup();
		}
	}
}
