package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ClientController {
	private Db db;
	private volatile String origin = "", session = "";
	private ScheduledExecutorService heartbeat;

	public synchronized void connect(String host, int port) throws Exception {
		disconnect();
		db = new Db();
		origin = "http://" + host + ":" + port;
		WebClient.Response opened = WebClient.request(origin + "/api/session/open", "POST", "");
		if (!opened.status().contains(" 200 ")) {
			throw new Exception("Không mở được phiên. Hãy khởi động lại server đã cập nhật.");
		}
		session = opened.headers().getOrDefault("x-lab-session", "");
		if (session.isBlank()) {
			throw new Exception(
					"Server chưa hỗ trợ phiên kết nối. Hãy cập nhật và khởi động lại server.");
		}
		WebClient.request(origin + "/", "HEAD", "", headersFor(origin));
		String server = origin, id = session;
		heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
		heartbeat.scheduleWithFixedDelay(
				() -> {
					try {
						WebClient.request(
								server + "/api/session/ping",
								"POST",
								"",
								Map.of("X-Lab-Session", id));
					} catch (Exception exception) {
						System.err.println("Heartbeat: " + exception.getMessage());
					}
				},
				10,
				10,
				TimeUnit.SECONDS);
	}

	public Map<String, String> headersFor(String target) {
		try {
			URI uRI = URI.create(origin), uRI2 = URI.create(target);
			int ap = uRI.getPort() < 0 ? 80 : uRI.getPort(),
					bp = uRI2.getPort() < 0 ? 80 : uRI2.getPort();
			if (!session.isBlank()
					&& Objects.equals(uRI.getScheme(), uRI2.getScheme())
					&& Objects.equals(uRI.getHost(), uRI2.getHost())
					&& ap == bp) {
				return Map.of("X-Lab-Session", session);
			}
		} catch (Exception ignored) {
		}
		return Map.of();
	}

	public synchronized void disconnect() {
		if (heartbeat != null) {
			heartbeat.shutdownNow();
			heartbeat = null;
		}
		String server = origin, id = session;
		session = "";
		origin = "";
		if (!id.isBlank()) {
			Thread.startVirtualThread(
					() -> {
						try {
							WebClient.request(
									server + "/api/session/close",
									"POST",
									"",
									Map.of("X-Lab-Session", id));
						} catch (Exception exception) {
							System.err.println("Disconnect: " + exception.getMessage());
						}
					});
		}
	}

	public WebClient.Response request(String url, String method, String body) throws Exception {
		WebClient.Response response = WebClient.request(url, method, body, headersFor(url));
		if (SaveOptions.enabled(SaveOptions.Kind.HISTORY)) {
			db.exec(
					"INSERT INTO history(url,method,status) VALUES(?,?,?)",
					url,
					method,
					response.status());
		}
		return response;
	}

	public static String downloadUrl(String url) {
		String base = url.split("#", 2)[0];
		return base.matches(".*[?&]download=1(?:&.*)?$")
				? base
				: base + (base.contains("?") ? "&" : "?") + "download=1";
	}

	public void download(String url, Path file) throws Exception {
		WebClient.Response response =
				WebClient.request(downloadUrl(url), "GET", "", headersFor(url));
		if (!response.status().contains(" 200 ")) {
			throw new Exception(response.status());
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, response.body());
		if (SaveOptions.enabled(SaveOptions.Kind.HISTORY)) {
			db.exec(
					"INSERT INTO history(url,method,status) VALUES(?,?,?)",
					url,
					"DOWNLOAD",
					response.status());
		}
	}

	public List<Object[]> history() throws Exception {
		return db.rows("SELECT at,method,url,status FROM history ORDER BY id DESC LIMIT 200");
	}

	public void recordVisit(String url, int status) throws Exception {
		if (SaveOptions.enabled(SaveOptions.Kind.HISTORY)) {
			db.exec(
					"INSERT INTO history(url,method,status) VALUES(?,?,?)",
					url,
					url.contains("download=1") ? "DOWNLOAD" : "GET",
					Integer.toString(status));
		}
	}
}
