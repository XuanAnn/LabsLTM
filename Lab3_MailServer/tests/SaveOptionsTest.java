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
			db.exec(
					"INSERT INTO users(name,password,email)"
							+ " VALUES('alice','123','alice@localhost')");
			check(db.auth("alice", "123"), "Existing account still authenticates");
			check(
					db.sessionStart("alice", "127.0.0.1", "TEST") == null
							&& db.count("sessions") == 0,
					"SESSION off skips JDBC session insertion");

			db.exec("INSERT INTO users(name,password,email) VALUES('bob','123','bob@localhost')");
			int smtp = IntegrationTest.port(), pop = IntegrationTest.port();
			IntegrationTest.start(
					"server.MailServer",
					Integer.toString(smtp),
					Integer.toString(pop),
					config.toString());
			IntegrationTest.ready(smtp);
			IntegrationTest.ready(pop);
			boolean rejected = false;
			try {
				MailClient.send(
						"127.0.0.1",
						smtp,
						false,
						"alice",
						"123",
						"alice@localhost",
						"bob@localhost",
						"Test",
						"Body");
			} catch (Exception e) {
				rejected = true;
			}
			check(
					rejected && db.count("mail") == 0,
					"MAIL_INBOX off rejects new local delivery without a false success");
			try (java.net.Socket socket = common.Wire.connect("127.0.0.1", pop)) {
				var in = socket.getInputStream();
				var out = socket.getOutputStream();
				common.Wire.line(in);
				for (String command : List.of("USER alice", "PASS 123", "XSAVE SENT")) {
					common.Wire.line(out, command);
					if (!common.Wire.line(in).startsWith("+OK"))
						throw new AssertionError(command);
				}
				MailClient.writeMessage(
						out,
						MailClient.message(
								"alice@localhost", "bob@localhost", "Saved copy", "Body"));
				check(
						common.Wire.line(in).startsWith("+OK")
								&& db.count("sent_mail") == 0,
						"MAIL_SENT off omits the sent copy");
				common.Wire.line(out, "QUIT");
				common.Wire.line(in);
			}
			check(
					!Files.exists(db.dataDirectory().resolve("accounts/accounts.txt")),
					"TEXT_EXPORT off creates no TXT mirror");

			System.out.println("ALL " + checks + " SAVE OPTIONS CHECKS PASSED");
		} finally {
			for (Process process : IntegrationTest.processes)
				if (process.isAlive()) IntegrationTest.stop(process);
			TestDatabase.cleanup();
		}
	}
}
