package common;

import client.*;
import server.*;
import common.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** A readable server-side mirror of the account and mailbox tables. MySQL remains authoritative. */
public final class DataFiles {
	private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

	private DataFiles() {}

	private record Account(
			String username, String email, String password, String created, String emailIp) {}

	private record Mail(
			long id,
			String folder,
			String owner,
			String sender,
			String recipient,
			String uid,
			String created,
			byte[] body) {}

	public static void sync(Db db, Path directory) throws Exception {
		Path root = directory.toAbsolutePath().normalize();
		synchronized (LOCKS.computeIfAbsent(root, p -> new Object())) {
			List<Account> accounts = new ArrayList<>();
			List<Mail> mails = new ArrayList<>();
			Map<String, String> emails = new HashMap<>(), ips = new HashMap<>();
			try (Connection connection = db.open()) {
				connection.setAutoCommit(false);
				try (Statement statement = connection.createStatement();
						ResultSet resultSet =
								statement.executeQuery(
										"SELECT name,email,password,created_at,last_ip FROM users"
												+ " ORDER BY name")) {
					while (resultSet.next()) {
						Account account =
								new Account(
										resultSet.getString(1),
										resultSet.getString(2),
										resultSet.getString(3),
										resultSet.getString(4),
										resultSet.getString(5));
						accounts.add(account);
						emails.put(account.username(), account.email());
						if (account.emailIp() != null) {
							ips.put(account.username(), MailText.ipOnly(account.emailIp()));
						}
					}
				}
				try (Statement statement2 = connection.createStatement();
						ResultSet resultSet2 =
								statement2.executeQuery(
										"SELECT id,recipient,sender,body,uid,created_at FROM mail"
												+ " ORDER BY id")) {
					while (resultSet2.next()) {
						String owner = resultSet2.getString(2);
						mails.add(
								new Mail(
										resultSet2.getLong(1),
										"inbox",
										owner,
										resultSet2.getString(3),
										emails.getOrDefault(owner, owner + "@localhost"),
										resultSet2.getString(5),
										resultSet2.getString(6),
										resultSet2.getBytes(4)));
					}
				}
				try (Statement statement3 = connection.createStatement();
						ResultSet resultSet3 =
								statement3.executeQuery(
										"SELECT id,owner,sender,recipient,uid,created_at,body FROM"
												+ " sent_mail ORDER BY id")) {
					while (resultSet3.next()) {
						mails.add(
								new Mail(
										resultSet3.getLong(1),
										"sent",
										resultSet3.getString(2),
										resultSet3.getString(3),
										resultSet3.getString(4),
										resultSet3.getString(5),
										resultSet3.getString(6),
										resultSet3.getBytes(7)));
					}
				}
				connection.commit();
			}
			Path accountsRoot = root.resolve("accounts"), mailsRoot = root.resolve("mails");
			Files.createDirectories(accountsRoot);
			Files.createDirectories(mailsRoot);
			Set<Path> accountFiles = new HashSet<>(), mailFiles = new HashSet<>();
			List<String> accountIndex = new ArrayList<>(), mailIndex = new ArrayList<>();
			for (Account account2 : accounts) {
				Path file = inside(accountsRoot, account2.username() + "/account.txt");
				accountFiles.add(file);
				String text =
						"username: "
								+ property(account2.username())
								+ "\npassword: "
								+ property(account2.password())
								+ "\ncreatedat: "
								+ property(account2.created())
								+ "\n";
				write(file, text.getBytes(StandardCharsets.UTF_8));
				accountIndex.add(text);
			}
			for (Mail mail : mails) {
				Path file =
						inside(
								mailsRoot,
								mail.owner() + "/" + mail.folder() + "/" + mail.uid() + ".txt");
				mailFiles.add(file);
				MailText.Content content =
						MailText.parse(new String(mail.body(), StandardCharsets.UTF_8));
				String sender = content.field("X-Local-Sender-Name");
				if (sender.isBlank()) {
					sender = content.field("From");
				}
				String recipients = content.field("X-Local-Recipient-Names");
				if (recipients.isBlank()) {
					recipients = mail.folder().equals("inbox") ? mail.owner() : mail.recipient();
				}
				String recipientIps;
				if (content.field("X-Local-Delivery").equals("EXTERNAL")) {
					recipientIps = "Không xác định (bên ngoài)";
				} else {
					String[] targets = recipients.split(",");
					if (targets.length == 1) {
						recipientIps = ips.getOrDefault(targets[0].trim(), "Chưa kết nối");
					} else {
						List<String> addresses = new ArrayList<>();
						for (String target : targets) {
							String name = target.trim();
							addresses.add(name + "=" + ips.getOrDefault(name, "Chưa kết nối"));
						}
						recipientIps = String.join("; ", addresses);
					}
				}
				String at = content.field("Date");
				try {
					at =
							java.time.ZonedDateTime.parse(
											at,
											java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
									.format(
											java.time.format.DateTimeFormatter.ofPattern(
													"yyyy-MM-dd HH:mm:ss XXX"));
				} catch (Exception ignored) {
					if (at.isBlank()) {
						at = mail.created();
					}
				}
				String text =
						"IP gửi: "
								+ MailText.ipOnly(content.field("X-Local-Sender-IP"))
								+ "\nIP nhận: "
								+ recipientIps
								+ "\nNgười gửi: "
								+ sender
								+ "\nEmail gửi: "
								+ mail.sender()
								+ "\nNgười nhận: "
								+ recipients
								+ "\nEmail nhận: "
								+ mail.recipient()
								+ "\nTiêu đề: "
								+ content.field("Subject")
								+ "\nGửi lúc: "
								+ at
								+ "\nThư mục: "
								+ (mail.folder().equals("sent") ? "Đã gửi" : "Thư đến")
								+ "\nMã thư: "
								+ mail.uid()
								+ "\nNội dung:\n"
								+ content.body();
				write(
						file,
						(text.endsWith("\n") ? text : text + "\n")
								.getBytes(StandardCharsets.UTF_8));
				mailIndex.add(
						mail.owner()
								+ " | "
								+ mail.folder()
								+ " | "
								+ mail.uid()
								+ " | "
								+ root.relativize(file).toString().replace('\\', '/'));
			}
			write(
					accountsRoot.resolve("accounts.txt"),
					String.join("\n", accountIndex).getBytes(StandardCharsets.UTF_8));
			write(
					mailsRoot.resolve("mails.txt"),
					(String.join("\n", mailIndex) + "\n").getBytes(StandardCharsets.UTF_8));
			Files.deleteIfExists(accountsRoot.resolve("accounts.json"));
			Files.deleteIfExists(mailsRoot.resolve("mails.json"));
			removeStale(accountsRoot, accountFiles, true);
			removeStale(mailsRoot, mailFiles, false);
		}
	}

	private static Path inside(Path root, String relative) throws IOException {
		Path path = root.resolve(relative).normalize();
		if (!path.startsWith(root)) {
			throw new IOException("Invalid data file path");
		}
		for (Path filePath = path;
				filePath != null && filePath.startsWith(root);
				filePath = filePath.getParent()) {
			if (Files.isSymbolicLink(filePath)) {
				throw new IOException("Data folders cannot be symbolic links");
			}
		}
		return path;
	}

	private static void write(Path file, byte[] bytes) throws IOException {
		Files.createDirectories(file.getParent());
		if (Files.isRegularFile(file)
				&& Files.size(file) == bytes.length
				&& Arrays.equals(Files.readAllBytes(file), bytes)) {
			return;
		}
		Path temporary = Files.createTempFile(file.getParent(), ".mirror-", ".tmp");
		try {
			Files.write(temporary, bytes);
			try {
				Files.move(
						temporary,
						file,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void removeStale(Path root, Set<Path> keep, boolean accounts)
			throws IOException {
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				String name = file.getFileName().toString();
				boolean managed =
						accounts
								? name.equals("account.txt")
								: name.matches("(?:[a-fA-F0-9-]{36}|[a-f0-9]{64})\\.(?:txt|eml)");
				if (managed && !keep.contains(file)) {
					Files.delete(file);
				}
			}
		}
	}

	private static String property(String value) {
		return value == null
				? ""
				: value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
	}
}
