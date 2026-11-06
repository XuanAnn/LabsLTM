import client.*;
import server.*;
import common.*;
import common.*;

import java.nio.file.*;
import java.util.*;

/** Compiled against a temporary SaveOptions copy with its enable lines commented out. */
public class SaveOptionsTest {
	private static int checks;

	private static void check(boolean ok, String label) {
		if (!ok) throw new AssertionError(label);
		checks++;
		System.out.println("PASS " + label);
	}

	public static void main(String[] args) throws Exception {
		Path dir = Files.createTempDirectory(Path.of("target"), "save-options-").toAbsolutePath();
		Path config = TestDatabase.create(dir, "save");
		System.setProperty("lab.db.config", config.toString());
		IntegrationTest.dir = dir;
		IntegrationTest.mysqlConfig = config;
		try {
			Db db = new Db(config);
			for (var kind : SaveOptions.Kind.values())
				check(!SaveOptions.enabled(kind), "Commented enable line: " + kind);
			db.audit("This must not become a database row");
			check(db.count("audit") == 0, "AUDIT off adds no row");
			boolean accountRejected = false;
			try {
				db.register("newuser", "123");
			} catch (java.sql.SQLException e) {
				accountRejected = true;
			}
			check(
					accountRejected && db.count("users") == 0,
					"ACCOUNT off rejects new registration");
			db.exec("INSERT INTO users(name,password) VALUES('alice','123')");
			check(db.auth("alice", "123"), "Existing account still authenticates");
			check(
					db.sessionStart("alice", "127.0.0.1", "TEST") == null
							&& db.count("sessions") == 0,
					"SESSION off skips JDBC session insertion");

			int port = IntegrationTest.port();
			Path files = dir.resolve("public");
			Files.createDirectories(files);
			Files.writeString(files.resolve("index.html"), "hello");
			Files.writeString(files.resolve("file.txt"), "download");
			IntegrationTest.start("server.WebServer", Integer.toString(port), files.toString());
			IntegrationTest.ready(port);
			ClientController controller = new ClientController();
			controller.connect("127.0.0.1", port);
			String base = "http://127.0.0.1:" + port;
			controller.request(base + "/", "HEAD", "");
			controller.request(base + "/api/echo", "POST", "hello");
			Path saved = dir.resolve("download.txt");
			controller.download(base + "/file.txt", saved);
			check(
					Files.readString(saved).equals("download"),
					"Disabled logging preserves HEAD, POST and download");
			Thread.sleep(250);
			check(db.count("history") == 0, "HISTORY off adds no client history");
			check(db.count("requests") == 0, "REQUEST_LOG off adds no server request record");
			check(
					db.count("sessions") == 0 && !controller.headersFor(base).isEmpty(),
					"SESSION off uses an in-memory session without a database row");
			controller.disconnect();
			Thread.sleep(150);

			System.out.println("ALL " + checks + " SAVE OPTIONS CHECKS PASSED");
		} finally {
			for (Process process : IntegrationTest.processes)
				if (process.isAlive()) IntegrationTest.stop(process);
			TestDatabase.cleanup();
		}
	}
}
