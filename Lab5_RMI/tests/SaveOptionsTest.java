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

			Node node = new Node("A", 0, config);
			try {
				boolean rejected = false;
				try {
					node.put("key", "value", false);
				} catch (java.rmi.RemoteException e) {
					rejected = true;
				}
				check(
						rejected && db.count("entries") == 0,
						"RMI_DATA off rejects writes without a false success");
				check(node.snapshot().isEmpty(), "RMI_DATA off still allows reading existing data");
			} finally {
				java.rmi.server.UnicastRemoteObject.unexportObject(node, true);
			}

			System.out.println("ALL " + checks + " SAVE OPTIONS CHECKS PASSED");
		} finally {
			for (Process process : IntegrationTest.processes)
				if (process.isAlive()) IntegrationTest.stop(process);
			TestDatabase.cleanup();
		}
	}
}
