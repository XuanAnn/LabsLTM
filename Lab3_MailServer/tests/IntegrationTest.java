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

	static String popLine(InputStream in) throws Exception {
		String s = Wire.line(in);
		if (s == null || !s.startsWith("+OK")) throw new AssertionError(s);
		return s;
	}

	static void mail() throws Exception {
		int smtp = port(), pop = port();
		Path db = mysqlConfig;
		Process server = start("server.MailServer", "" + smtp, "" + pop, db.toString());
		ready(smtp);
		ready(pop);
		String body = "Xin chào\n.dot\n..double";
		check(
				MailClient.register("localhost", pop, "charlie", "123").startsWith("+OK"),
				"Mail registration through POP3 XREGISTER extension");
		MailClient.send(
				"localhost",
				smtp,
				false,
				"alice",
				"123",
				"alice@localhost",
				"charlie@localhost",
				"Registered mailbox",
				"hello");
		check(
				MailClient.inbox("localhost", pop, "charlie", "123", 1).contains("Subject:"),
				"Newly registered mail user can receive and log in");
		boolean badLogin = false;
		try {
			MailClient.send(
					"localhost",
					smtp,
					false,
					"alice",
					"wrong",
					"alice@localhost",
					"bob@localhost",
					"wrong login",
					"x");
		} catch (IOException e) {
			badLogin = true;
		}
		check(badLogin, "SMTP identifies sender through AUTH LOGIN");
		MailClient.send(
				"localhost",
				smtp,
				false,
				"alice",
				"123",
				"alice@localhost",
				"bob@localhost",
				"Thử Unicode",
				body);
		check(
				MailClient.inbox("localhost", pop, "bob", "123", 0).contains("1 "),
				"SMTP delivers to POP3 mailbox");
		String message = MailClient.inbox("localhost", pop, "bob", "123", 1);
		check(
				message.contains(
						Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8))),
				"Mail UTF-8 MIME body preserved");
		boolean rejected = false;
		try {
			MailClient.inbox("localhost", pop, "bob", "wrong", 0);
		} catch (IOException e) {
			rejected = true;
		}
		check(rejected, "POP3 verifies mailbox identity");
		boolean denied = false;
		try {
			MailClient.send(
					"localhost",
					smtp,
					false,
					"alice",
					"123",
					"alice@localhost",
					"someone@example.org",
					"External route",
					"x");
		} catch (IOException e) {
			denied = true;
		}
		check(denied, "SMTP reports external routing is handled by Gmail client");
		try (Socket s = Wire.connect("localhost", pop)) {
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			popLine(in);
			Wire.line(out, "USER bob");
			popLine(in);
			Wire.line(out, "PASS 123");
			popLine(in);
			Wire.line(out, "DELE 1");
			popLine(in);
			// Drop without QUIT: deletion must roll back.
		}
		Thread.sleep(150);
		check(
				MailClient.inbox("localhost", pop, "bob", "123", 0).contains("1 "),
				"POP3 DELE rolled back on disconnect");
		stop(server);
		server = start("server.MailServer", "" + smtp, "" + pop, db.toString());
		ready(smtp);
		ready(pop);
		check(
				MailClient.inbox("localhost", pop, "bob", "123", 1).contains("Subject:"),
				"Mail persists across restart");
		try (Socket s = Wire.connect("localhost", pop)) {
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			popLine(in);
			Wire.line(out, "USER bob");
			popLine(in);
			Wire.line(out, "PASS 123");
			popLine(in);
			Wire.line(out, "DELE 1");
			popLine(in);
			Wire.line(out, "QUIT");
			popLine(in);
		}
		check(
				!MailClient.inbox("localhost", pop, "bob", "123", 0).contains("1 "),
				"POP3 QUIT commits deletion");
		Db database = new Db(mysqlConfig);
		check(
				database.auth("charlie", "123")
						&& !database.rows("SELECT body FROM mail WHERE recipient='charlie'")
								.isEmpty(),
				"MySQL stores registered users and mailbox bytes");
		ClientController controller = new ClientController();
		controller.login(
				new common.ClientWindow.Login("localhost", smtp, pop, "charlie", "123"));
		check(
				controller.inbox().getFirst().subject().equals("Registered mailbox")
						&& controller.inbox().getFirst().body().equals("hello"),
				"Mail UI controller decodes MIME subject and UTF-8 body");
		check(
				MailClient.register("localhost", pop, "diana", "123", "diana@example.test")
						.startsWith("+OK"),
				"Register username together with a distinct email");
		ClientController sender = new ClientController();
		sender.login(new ClientWindow.Login("localhost", smtp, pop, "diana", "123"));
		check(
				sender.email().equals("diana@example.test"),
				"Login retrieves email belonging to the account");
		sender.send("charlie@localhost", "Thư thứ nhất", "Tên tài khoản và email người gửi");
		var firstSent = sender.sent().getFirst();
		check(
				firstSent.from().equals("diana <diana@example.test>")
						&& firstSent.to().equals("charlie@localhost"),
				"Sent mail stores both sender name and email");
		var received =
				controller.inbox().stream()
						.filter(m -> m.subject().equals("Thư thứ nhất"))
						.findFirst()
						.orElseThrow();
		check(
				received.from().equals("diana <diana@example.test>"),
				"Recipient sees username and email in From header");
		sender.send("charlie@localhost", "Thư thứ hai", "Thư đến sau khi danh sách đã được tải");
		sender.delete("SENT", firstSent.uid());
		check(
				sender.sent().stream().noneMatch(m -> m.uid().equals(firstSent.uid()))
						&& sender.sent().stream().anyMatch(m -> m.subject().equals("Thư thứ hai")),
				"Delete a sent message by stable UID after new mail arrives");
		check(
				controller.inbox().stream().anyMatch(m -> m.uid().equals(received.uid())),
				"Deleting sender copy does not delete recipient copy");
		controller.delete("INBOX", received.uid());
		check(
				controller.inbox().stream().noneMatch(m -> m.uid().equals(received.uid()))
						&& controller.inbox().stream()
								.anyMatch(m -> m.subject().equals("Thư thứ hai")),
				"Inbox deletion targets the selected UID without deleting newer mail");
		byte[] externalCopy =
				MailClient.message(
						"diana <diana@example.test>",
						"friend@example.test",
						"Bản sao thư bên ngoài",
						"Nội dung đã gửi");
		sender.saveSent(externalCopy);
		sender.saveSent(externalCopy);
		check(
				sender.sent().stream()
								.filter(m -> m.subject().equals("Bản sao thư bên ngoài"))
								.count()
						== 1,
				"External sent copy is saved once without duplicate records");
		java.util.concurrent.atomic.AtomicInteger accepted =
				new java.util.concurrent.atomic.AtomicInteger();
		ClientController gmailFlow =
				new ClientController(
						(settings, recipient, raw) -> {
							check(
									ClientController.decode(
													0, new String(raw, StandardCharsets.UTF_8))
											.from()
											.equals("diana <demo@gmail.test>"),
									"Gmail delivery receives account name and configured email");
							accepted.incrementAndGet();
						});
		gmailFlow.login(new ClientWindow.Login("localhost", smtp, pop, "diana", "123"));
		gmailFlow.sendGmail(
				"friend@example.test",
				"Gmail flow persisted",
				"Lưu thư Gmail trong MySQL",
				new GmailSettings("demo@gmail.test", "test-only"));
		check(
				accepted.get() == 1
						&& gmailFlow.sent().stream()
								.anyMatch(m -> m.subject().equals("Gmail flow persisted")),
				"The actual sendGmail path saves its accepted message in server MySQL");
		check(
				!database.rows(
								"SELECT owner,sender,recipient FROM sent_mail WHERE owner=? AND"
										+ " sender=? AND recipient=?",
								"diana",
								"demo@gmail.test",
								"friend@example.test")
						.isEmpty(),
				"Gmail sent database row stores local owner and sender/recipient emails");
		var gmailCopy =
				gmailFlow.sent().stream()
						.filter(m -> m.subject().equals("Gmail flow persisted"))
						.findFirst()
						.orElseThrow();
		Path gmailFile =
				database.dataDirectory().resolve("mails/diana/sent/" + gmailCopy.uid() + ".txt");
		check(
				Files.readString(gmailFile, StandardCharsets.UTF_8)
								.contains("Email gửi: demo@gmail.test")
						&& Files.readString(gmailFile, StandardCharsets.UTF_8)
								.contains("Tiêu đề: Gmail flow persisted"),
				"The Gmail send path also creates a readable sent TXT in the server folder");
		check(
				gmailCopy.senderIp().equals("127.0.0.1")
						&& gmailCopy.recipientIp().equals("Không xác định (bên ngoài)"),
				"External mail records the observed local sender IP without inventing a remote"
						+ " recipient IP");
		ClientController failedGmail =
				new ClientController(
						(settings, recipient, raw) -> {
							throw new IOException("Test rejection");
						});
		failedGmail.login(new ClientWindow.Login("localhost", smtp, pop, "diana", "123"));
		boolean failed = false;
		try {
			failedGmail.sendGmail(
					"friend@example.test",
					"Rejected external message",
					"Không gửi",
					new GmailSettings("demo@gmail.test", "test-only"));
		} catch (IOException e) {
			failed = true;
		}
		check(
				failed
						&& failedGmail.sent().stream()
								.noneMatch(m -> m.subject().equals("Rejected external message")),
				"Rejected external delivery is not falsely recorded as sent");
		sender.updateEmail("diana.new@example.test");
		check(
				database.email("diana").equals("diana.new@example.test"),
				"Account email can be updated and persists in MySQL");
		controller.send(
				"diana.new@example.test", "Địa chỉ hồ sơ", "Gửi tới email đã gắn tài khoản");
		check(
				sender.inbox().stream().anyMatch(m -> m.subject().equals("Địa chỉ hồ sơ")),
				"Local delivery resolves a registered email to its account");
		stop(server);
		server = start("server.MailServer", "" + smtp, "" + pop, db.toString());
		ready(smtp);
		ready(pop);
		check(
				sender.sent().stream().anyMatch(m -> m.subject().equals("Bản sao thư bên ngoài"))
						&& sender.sent().stream().noneMatch(m -> m.uid().equals(firstSent.uid())),
				"Sent mail persists and deleted copies do not return after restart");
		check(
				new GmailSettings("test@example.test", "ab cd ef").password().equals("abcdef"),
				"App password formatting removes presentation spaces");
		Path legacyConfig = TestDatabase.create(dir, "legacy");
		Db legacy = new Db(legacyConfig);
		legacy.exec("ALTER TABLE users DROP COLUMN email");
		legacy.exec("DROP TABLE sent_mail");
		legacy.exec("DELETE FROM mail_schema_migrations WHERE name='sent-v1'");
		legacy.exec(
				"INSERT INTO mail(recipient,sender,body,uid) VALUES(?,?,?,?)",
				"bob",
				"alice@localhost",
				MailClient.message("alice@localhost", "bob@localhost", "Thư cũ", "Giữ dữ liệu cũ"),
				UUID.randomUUID().toString());
		legacy = new Db(legacyConfig);
		check(
				legacy.auth("alice", "123")
						&& legacy.email("alice").equals("alice@localhost")
						&& legacy.count("sent_mail") == 1,
				"Old database is upgraded without losing accounts and sent history");
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

	static long inboxCount(Db db, String user) throws Exception {
		return ((Number) db.rows("SELECT COUNT(*) FROM mail WHERE recipient=?", user).getFirst()[0])
				.longValue();
	}

	static void localGroupsAndFiles() throws Exception {
		Path config = TestDatabase.create(dir, "groups");
		Db db = new Db(config);
		int smtp = port(), pop = port();
		Process server = start("server.MailServer", "" + smtp, "" + pop, config.toString());
		ready(smtp);
		ready(pop);
		MailClient.register("localhost", pop, "carol", "123");
		MailClient.register("localhost", pop, "dave", "123", "dave@example.test");
		ClientController alice =
				new ClientController(
						(settings, to, message) -> {
							throw new AssertionError("Local mail must not use Gmail");
						});
		alice.login(new ClientWindow.Login("localhost", smtp, pop, "alice", "123"));
		ClientController bob = new ClientController();
		bob.login(new ClientWindow.Login("localhost", smtp, pop, "bob", "123"));
		bob.updateEmail("bob@example.test");
		var contacts = alice.contacts();
		check(
				contacts.size() == 4
						&& contacts.stream()
								.anyMatch(
										c ->
												c.username().equals("bob")
														&& c.email().equals("bob@example.test")),
				"Recipient directory contains registered names and emails including offline"
						+ " accounts");
		check(
				ClientController.recipientAddresses(
								"Bob <bob@localhost>, BOB@localhost; carol@localhost")
						.equals(List.of("bob@localhost", "carol@localhost")),
				"Recipient selection parses names and deduplicates email addresses");
		alice.sendGroup(
				List.of("bob@localhost", "bob@example.test", "carol@localhost"),
				"Nhóm được chọn",
				"Nội dung gửi nhóm\nDòng thứ hai");
		check(
				inboxCount(db, "bob") == 1
						&& inboxCount(db, "carol") == 1
						&& inboxCount(db, "dave") == 0
						&& inboxCount(db, "alice") == 0,
				"Group send reaches selected accounts once, including alias duplicates, and"
						+ " excludes outsiders");
		var groupSent = alice.sent().getFirst();
		check(
				alice.sent().size() == 1 && groupSent.recipientNames().equals("bob, carol"),
				"One sent record lists the actual group recipients");
		String carolUid =
				db.rows("SELECT uid FROM mail WHERE recipient='carol'").getFirst()[0].toString();
		Path data = db.dataDirectory(),
				accountFile = data.resolve("accounts/alice/account.txt"),
				carolFile = data.resolve("mails/carol/inbox/" + carolUid + ".txt"),
				sentFile = data.resolve("mails/alice/sent/" + groupSent.uid() + ".txt");
		List<String> accountLines = Files.readAllLines(accountFile, StandardCharsets.UTF_8);
		check(
				accountLines.size() == 3
						&& accountLines.get(0).equals("username: alice")
						&& accountLines.get(1).equals("password: 123")
						&& accountLines.get(2).startsWith("createdat: "),
				"Account TXT stores username, password and createdat on separate lines");
		String text = Files.readString(carolFile, StandardCharsets.UTF_8);
		check(
				text.contains("IP gửi: 127.0.0.1\n")
						&& text.contains("IP nhận: Chưa kết nối")
						&& text.contains("Người gửi: alice")
						&& text.contains("Người nhận: carol")
						&& text.contains("Tiêu đề: Nhóm được chọn")
						&& text.contains("Gửi lúc: ")
						&& text.contains("Nội dung gửi nhóm\nDòng thứ hai"),
				"Mailbox TXT contains readable sender/receiver IP, names, subject, timestamp and"
						+ " multiline body");
		var bobMail = bob.inbox().getFirst();
		check(
				bobMail.senderIp().equals("127.0.0.1") && bobMail.recipientIp().equals("127.0.0.1"),
				"Inbox exposes sender and receiver IP without ports");
		ClientController carol = new ClientController();
		carol.login(new ClientWindow.Login("localhost", smtp, pop, "carol", "123"));
		check(
				carol.inbox().getFirst().recipientIp().equals("127.0.0.1")
						&& Files.readString(carolFile).contains("IP nhận: 127.0.0.1\n"),
				"Offline recipient obtains its observed receiver IP after connecting");
		MailClient.register("localhost", pop, "eve", "123");
		alice.sendBroadcast(
				false, "Thông báo tất cả", "Gửi mọi tài khoản khác, cả người chưa kết nối");
		check(
				inboxCount(db, "bob") == 2
						&& inboxCount(db, "carol") == 2
						&& inboxCount(db, "dave") == 1
						&& inboxCount(db, "eve") == 1
						&& inboxCount(db, "alice") == 0,
				"Broadcast resolves all current local accounts on the server, including newly"
						+ " registered and offline users");
		check(
				alice.sent().size() == 2
						&& alice.sent().getFirst().to().equals("Tất cả tài khoản trên máy chủ"),
				"Broadcast is archived once and displays its delivery scope");
		alice.sendBroadcast(true, "Gửi cả cho tôi", "Thông báo gồm tài khoản gửi");
		check(
				inboxCount(db, "alice") == 1 && inboxCount(db, "eve") == 2,
				"Broadcast can include the sending account explicitly");
		long before = db.count("mail"), sentBefore = db.count("sent_mail");
		boolean invalid = false;
		try {
			alice.sendGroup(
					List.of("bob@localhost", "missing@localhost"),
					"Không gửi một phần",
					"Không được giao thư");
		} catch (IOException e) {
			invalid = true;
		}
		check(
				invalid && db.count("mail") == before && db.count("sent_mail") == sentBefore,
				"An invalid group recipient aborts before DATA without partial delivery or a false"
						+ " sent copy");
		alice.delete("SENT", groupSent.uid());
		check(
				!Files.exists(sentFile) && Files.exists(carolFile),
				"Deleting a sent record removes its TXT while retaining recipient TXT copies");
		bob.delete("INBOX", bobMail.uid());
		check(
				!Files.exists(data.resolve("mails/bob/inbox/" + bobMail.uid() + ".txt"))
						&& Files.exists(carolFile),
				"Deleting an inbox message removes only the selected account TXT");
		try (var files = Files.walk(data)) {
			check(
					files.filter(Files::isRegularFile).allMatch(p -> p.toString().endsWith(".txt")),
					"Server local data uses TXT files instead of JSON or EML");
		}
		stop(server);
		server = start("server.MailServer", "" + smtp, "" + pop, config.toString());
		ready(smtp);
		ready(pop);
		check(
				alice.sent().stream().anyMatch(m -> m.subject().equals("Thông báo tất cả"))
						&& Files.isRegularFile(accountFile)
						&& Files.isRegularFile(carolFile)
						&& !Files.exists(sentFile),
				"Restart rebuilds account/mail TXT mirrors without restoring deleted mail");
	}

	public static void main(String[] args) throws Exception {
		Files.createDirectories(Path.of("target"));
		dir = Files.createTempDirectory(Path.of("target"), "integration-").toAbsolutePath();
		System.out.println("Artifacts: " + dir);
		mysqlConfig = TestDatabase.create(dir, "main");
		System.setProperty("lab.db.config", mysqlConfig.toString());
		try {
			mail();
			localGroupsAndFiles();
			lifecycle();
			System.out.println("ALL " + checks + " CHECKS PASSED");
		} finally {
			for (Process p : processes) if (p.isAlive()) stop(p);
			TestDatabase.cleanup();
		}
	}
}
