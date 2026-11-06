import client.*;
import server.*;
import common.*;

import java.net.*;
import java.nio.file.*;

public class BrowserFixture {
	public static void main(String[] args) throws Exception {
		Path dir = Files.createTempDirectory(Path.of("target"), "browser-").toAbsolutePath();
		Path config = TestDatabase.create(dir, "browser");
		System.setProperty("lab.db.config", config.toString());
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		try (WebServer server = new WebServer(Path.of("public"))) {
			Thread.startVirtualThread(
					() -> {
						try {
							server.serve(port);
						} catch (Exception ignored) {
						}
					});
			System.out.println("READY " + port);
			System.out.flush();
			System.in.read();
		} finally {
			TestDatabase.cleanup();
		}
	}
}
