package server;

import client.*;
import server.*;
import common.*;

import common.*;
import common.MailText;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/** Local SMTP + POP3. External delivery is done explicitly by the SMTP client. */
public final class MailServer implements AutoCloseable {
	private final Db db;
	private final Set<String> locks = ConcurrentHashMap.newKeySet();
	private final Set<ServerSocket> listeners = ConcurrentHashMap.newKeySet();
	private final Set<Socket> clients = ConcurrentHashMap.newKeySet();

	public MailServer(Path path) throws Exception {
		db = new Db(path);
		db.user("alice", "123");
		db.user("bob", "123");
	}

	public void serve(int smtp, int pop) throws Exception {
		try (ServerSocket smtpSocket = new ServerSocket(smtp);
				ServerSocket popSocket = new ServerSocket(pop)) {
			listeners.add(smtpSocket);
			listeners.add(popSocket);
			Thread.startVirtualThread(
					() -> {
						try {
							listen(popSocket, false);
						} catch (Exception exception) {
							if (!popSocket.isClosed()) {
								System.err.println(exception.getMessage());
							}
						}
					});
			listen(smtpSocket, true);
		}
	}

	private void listen(ServerSocket serverSocket, boolean smtp) throws Exception {
		try (serverSocket) {
			System.out.println((smtp ? "SMTP" : "POP3") + " :" + serverSocket.getLocalPort());
			while (!serverSocket.isClosed()) {
				Socket socket = serverSocket.accept();
				clients.add(socket);
				Thread.startVirtualThread(
						() -> {
							if (smtp) {
								smtp(socket);
							} else {
								pop(socket);
							}
						});
			}
		}
	}

	private static String address(String arg, String prefix) throws IOException {
		if (!arg.regionMatches(true, 0, prefix, 0, prefix.length())) {
			throw new IOException("Syntax");
		}
		String text = arg.substring(prefix.length()).trim();
		if (!text.startsWith("<") || text.indexOf('>') < 0) {
			throw new IOException("Use <address>");
		}
		String text2 = text.substring(1, text.indexOf('>'));
		if (!text2.matches("[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+")) {
			throw new IOException("Invalid address");
		}
		return text2;
	}

	private void smtp(Socket socket) {
		String sessionId = null;
		try (socket) {
			socket.setSoTimeout(120000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			Wire.line(out, "220 localhost LTM SMTP");
			boolean greeted = false;
			String from = null, identity = null;
			List<String> to = new ArrayList<>(), envelopeRecipients = new ArrayList<>();
			for (String line; (line = Wire.line(in)) != null; ) {
				String[] parts = line.split(" ", 2);
				String cmd = parts[0].toUpperCase(Locale.ROOT),
						arg = parts.length > 1 ? parts[1] : "";
				try {
					switch (cmd) {
						case "QUIT" -> {
							Wire.line(out, "221 Bye");
							return;
						}
						case "EHLO" -> {
							greeted = true;
							from = null;
							to.clear();
							envelopeRecipients.clear();
							Wire.line(out, "250-localhost");
							Wire.line(out, "250-AUTH LOGIN");
							Wire.line(out, "250-XRCPT ALL");
							Wire.line(out, "250 SIZE 10485760");
						}
						case "HELO" -> {
							greeted = true;
							from = null;
							to.clear();
							envelopeRecipients.clear();
							Wire.line(out, "250 localhost");
						}
						case "NOOP" -> Wire.line(out, "250 OK");
						case "RSET" -> {
							from = null;
							to.clear();
							envelopeRecipients.clear();
							Wire.line(out, "250 Reset");
						}
						case "AUTH" -> {
							if (!greeted) {
								Wire.line(out, "503 EHLO first");
								break;
							}
							if (!arg.equalsIgnoreCase("LOGIN")) {
								Wire.line(out, "504 Use AUTH LOGIN");
								break;
							}
							Wire.line(out, "334 VXNlcm5hbWU6");
							String account =
									new String(
													Base64.getDecoder()
															.decode(
																	Objects.requireNonNull(
																			Wire.line(in))),
													StandardCharsets.UTF_8)
											.toLowerCase(Locale.ROOT);
							Wire.line(out, "334 UGFzc3dvcmQ6");
							String pass =
									new String(
											Base64.getDecoder()
													.decode(Objects.requireNonNull(Wire.line(in))),
											StandardCharsets.UTF_8);
							identity = db.auth(account, pass) ? account : null;
							if (identity != null) {
								db.sessionEnd(sessionId);
								sessionId =
										db.sessionStart(
												identity,
												socket.getRemoteSocketAddress().toString(),
												"SMTP");
							}
							Wire.line(
									out,
									identity != null
											? "235 Logged in as " + identity
											: "535 Incorrect username/password");
						}
						case "MAIL" -> {
							if (!greeted) {
								Wire.line(out, "503 EHLO first");
								break;
							}
							if (identity == null) {
								Wire.line(out, "530 AUTH LOGIN first");
								break;
							}
							String sender = address(arg, "FROM:").toLowerCase(Locale.ROOT);
							if (!sender.equals(identity + "@localhost")
									&& !sender.equalsIgnoreCase(db.email(identity))) {
								Wire.line(out, "553 Sender must match logged-in user");
								break;
							}
							from = sender;
							to.clear();
							envelopeRecipients.clear();
							Wire.line(out, "250 Sender OK");
						}
						case "RCPT" -> {
							if (from == null) {
								Wire.line(out, "503 MAIL first");
								break;
							}
							String text2 = address(arg, "TO:").toLowerCase(Locale.ROOT);
							String user;
							List<Object[]> users =
									db.rows(
											"SELECT name FROM users WHERE email=? OR"
													+ " CONCAT(name,'@localhost')=?",
											text2,
											text2);
							if (!users.isEmpty()) {
								user = users.getFirst()[0].toString();
							} else {
								Wire.line(
										out,
										"550 Unknown local address; use external delivery for other"
												+ " addresses");
								break;
							}
							if (to.size() >= 10000) {
								Wire.line(out, "452 Too many recipients");
								break;
							}
							if (!to.contains(user)) {
								to.add(user);
								envelopeRecipients.add(text2);
							}
							Wire.line(out, "250 Recipient OK");
						}
						case "XRCPT" -> {
							if (from == null) {
								Wire.line(out, "503 MAIL first");
								break;
							}
							if (!arg.equalsIgnoreCase("ALL") && !arg.equalsIgnoreCase("ALL SELF")) {
								Wire.line(out, "501 XRCPT ALL [SELF]");
								break;
							}
							boolean self = arg.equalsIgnoreCase("ALL SELF");
							List<Object[]> users =
									db.rows("SELECT name,email FROM users ORDER BY name");
							if (users.size() > 10000) {
								Wire.line(out, "452 Too many recipients");
								break;
							}
							to.clear();
							envelopeRecipients.clear();
							for (Object[] row : users) {
								String name = row[0].toString();
								if (!self && name.equalsIgnoreCase(identity)) {
									continue;
								}
								to.add(name);
								envelopeRecipients.add(
										row[1] == null ? name + "@localhost" : row[1].toString());
							}
							Wire.line(
									out,
									to.isEmpty()
											? "550 No local recipients"
											: "250 " + to.size() + " local recipients selected");
						}
						case "DATA" -> {
							if (!SaveOptions.enabled(SaveOptions.Kind.MAIL_INBOX)) {
								Wire.line(out, "550 Local delivery disabled by server");
								break;
							}
							if (from == null || to.isEmpty()) {
								Wire.line(out, "503 MAIL/RCPT first");
								break;
							}
							Wire.line(out, "354 End with <CRLF>.<CRLF>");
							ByteArrayOutputStream buffer = new ByteArrayOutputStream();
							boolean large = false;
							while (true) {
								String text = Wire.line(in);
								if (text == null) {
									throw new EOFException();
								}
								if (text.equals(".")) {
									break;
								}
								if (text.startsWith("..")) {
									text = text.substring(1);
								}
								byte[] row = (text + "\r\n").getBytes(StandardCharsets.UTF_8);
								if (buffer.size() + row.length > 10485760) {
									large = true;
								}
								if (!large) {
									buffer.write(row);
								}
							}
							if (large) {
								Wire.line(out, "552 Message too large");
							} else {
								try (Connection connection = db.open()) {
									connection.setAutoCommit(false);
									try (PreparedStatement statement =
											connection.prepareStatement(
													"INSERT INTO mail(recipient,sender,body,uid)"
															+ " VALUES(?,?,?,?)")) {
										Map<String, String> ips = accountIps();
										String senderIp = socket.getInetAddress().getHostAddress();
										for (String user : new HashSet<>(to)) {
											statement.setString(1, user);
											statement.setString(2, from);
											statement.setBytes(
													3,
													deliveryDetails(
															buffer.toByteArray(),
															identity,
															List.of(user),
															senderIp,
															false,
															ips));
											statement.setString(4, UUID.randomUUID().toString());
											statement.addBatch();
										}
										statement.executeBatch();
										saveSent(
												connection,
												identity,
												from,
												String.join(",", envelopeRecipients),
												buffer.toByteArray(),
												to,
												senderIp,
												false,
												ips);
										connection.commit();
									}
								}
								db.syncData();
								try {
									db.audit("MAIL " + from + " -> " + String.join(",", to));
								} catch (Exception exception) {
									System.err.println("Audit: " + exception.getMessage());
								}
								Wire.line(out, "250 Queued locally");
							}
							from = null;
							to.clear();
							envelopeRecipients.clear();
						}
						default -> Wire.line(out, "502 Unsupported command");
					}
				} catch (IOException exception2) {
					Wire.line(out, "501 Invalid syntax");
				}
			}
		} catch (Exception exception3) {
			System.err.println("SMTP: " + exception3.getMessage());
		} finally {
			clients.remove(socket);
			try {
				db.sessionEnd(sessionId);
			} catch (Exception exception4) {
				System.err.println(exception4.getMessage());
			}
		}
	}

	record Message(long id, byte[] body, String uid) {}

	private static void saveSent(
			Connection connection,
			String owner,
			String sender,
			String recipient,
			byte[] body,
			List<String> names,
			String senderIp,
			boolean external,
			Map<String, String> ips)
			throws Exception {
		if (!SaveOptions.enabled(SaveOptions.Kind.MAIL_SENT)) {
			return;
		}
		String uid =
				HexFormat.of()
						.formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));
		try (PreparedStatement statement =
				connection.prepareStatement(
						"INSERT IGNORE INTO sent_mail(owner,recipient,sender,body,uid)"
								+ " VALUES(?,?,?,?,?)")) {
			statement.setString(1, owner);
			statement.setString(2, recipient);
			statement.setString(3, sender);
			statement.setBytes(4, deliveryDetails(body, owner, names, senderIp, external, ips));
			statement.setString(5, uid);
			statement.executeUpdate();
		}
	}

	private Map<String, String> accountIps() throws Exception {
		Map<String, String> result = new HashMap<>();
		for (Object[] row : db.rows("SELECT name,last_ip FROM users")) {
			if (row[1] != null) {
				result.put(row[0].toString(), MailText.ipOnly(row[1].toString()));
			}
		}
		return result;
	}

	private static byte[] deliveryDetails(
			byte[] body,
			String sender,
			List<String> names,
			String senderIp,
			boolean external,
			Map<String, String> ips) {
		String recipients = String.join(", ", new LinkedHashSet<>(names));
		String recipientIps;
		if (external) {
			recipientIps = "Không xác định (bên ngoài)";
		} else if (names.size() == 1) {
			recipientIps = ips.getOrDefault(names.getFirst(), "Chưa kết nối");
		} else {
			List<String> addresses = new ArrayList<>();
			for (String name : new LinkedHashSet<>(names)) {
				String address = ips.getOrDefault(name, "Chưa kết nối");
				addresses.add(name + "=" + address);
			}
			recipientIps = String.join("; ", addresses);
		}
		Map<String, String> details = new LinkedHashMap<>();
		details.put("X-Local-Sender-IP", MailText.ipOnly(senderIp));
		details.put("X-Local-Recipient-IP", recipientIps);
		details.put("X-Local-Sender-Name", sender);
		details.put("X-Local-Recipient-Names", recipients);
		String delivery = "DIRECT";
		if (external) {
			delivery = "EXTERNAL";
		} else if (header(body, "To").equals("all-local:;")) {
			delivery = "BROADCAST";
		} else if (names.size() > 1 || header(body, "To").contains(",")) {
			delivery = "GROUP";
		}
		details.put("X-Local-Delivery", delivery);
		return MailText.details(body, details);
	}

	private static String header(byte[] body, String name) {
		for (String row : new String(body, StandardCharsets.UTF_8).split("\r\n")) {
			if (row.isEmpty()) {
				break;
			}
			if (row.regionMatches(true, 0, name + ":", 0, name.length() + 1)) {
				return row.substring(name.length() + 1).trim();
			}
		}
		return "";
	}

	private static byte[] readMessage(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		boolean large = false;
		while (true) {
			String row = Wire.line(in);
			if (row == null) {
				throw new EOFException();
			}
			if (row.equals(".")) {
				break;
			}
			if (row.startsWith("..")) {
				row = row.substring(1);
			}
			byte[] bytes = (row + "\r\n").getBytes(StandardCharsets.UTF_8);
			if (buffer.size() + bytes.length > 10485760) {
				large = true;
			}
			if (!large) {
				buffer.write(bytes);
			}
		}
		return large ? null : buffer.toByteArray();
	}

	private List<Message> messages(String user, boolean sent) throws Exception {
		List<Message> result = new ArrayList<>();
		Map<String, String> ips = accountIps();
		Map<Long, byte[]> changed = new HashMap<>();
		String sql =
				sent
						? "SELECT id,body,uid FROM sent_mail WHERE owner=? ORDER BY id"
						: "SELECT id,body,uid FROM mail WHERE recipient=? ORDER BY id";
		try (Connection connection = db.open();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, user);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					byte[] original = resultSet.getBytes(2);
					MailText.Content content =
							MailText.parse(new String(original, StandardCharsets.UTF_8));
					String sender = content.field("X-Local-Sender-Name");
					if (sender.isBlank()) {
						sender = sent ? user : content.field("From");
					}
					String targets = content.field("X-Local-Recipient-Names");
					List<String> names = new ArrayList<>();
					if (sent && !targets.isBlank()) {
						for (String target : targets.split(",")) {
							names.add(target.trim());
						}
					} else {
						names.add(user);
					}
					byte[] body =
							deliveryDetails(
									original,
									sender,
									names,
									content.field("X-Local-Sender-IP"),
									content.field("X-Local-Delivery").equals("EXTERNAL"),
									ips);
					if (!Arrays.equals(body, original)) {
						changed.put(resultSet.getLong(1), body);
					}
					result.add(new Message(resultSet.getLong(1), body, resultSet.getString(3)));
				}
			}
		}
		if (!changed.isEmpty()) {
			try (Connection connection2 = db.open();
					PreparedStatement statement2 =
							connection2.prepareStatement(
									"UPDATE "
											+ (sent ? "sent_mail" : "mail")
											+ " SET body=? WHERE id=?")) {
				for (Map.Entry<Long, byte[]> entry : changed.entrySet()) {
					statement2.setBytes(1, entry.getValue());
					statement2.setLong(2, entry.getKey());
					statement2.addBatch();
				}
				statement2.executeBatch();
				db.syncData();
			}
		}
		return result;
	}

	private void pop(Socket socket) {
		String locked = null, sessionId = null;
		try (socket) {
			socket.setSoTimeout(120000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			Wire.line(out, "+OK LTM POP3");
			String user = "";
			boolean auth = false, sent = false;
			List<Message> list = List.of();
			Set<Integer> deleted = new HashSet<>();
			for (String line; (line = Wire.line(in)) != null; ) {
				String[] parts = line.split(" ", 2);
				String cmd = parts[0].toUpperCase(Locale.ROOT),
						arg = parts.length > 1 ? parts[1] : "";
				if (cmd.equals("QUIT")) {
					if (auth) {
						try (Connection connection = db.open()) {
							connection.setAutoCommit(false);
							try (PreparedStatement statement =
									connection.prepareStatement(
											sent
													? "DELETE FROM sent_mail WHERE id=? AND owner=?"
													: "DELETE FROM mail WHERE id=? AND"
															+ " recipient=?")) {
								for (int count : deleted) {
									statement.setLong(1, list.get(count - 1).id());
									statement.setString(2, user);
									statement.addBatch();
								}
								statement.executeBatch();
								connection.commit();
							}
						}
					}
					if (auth && !deleted.isEmpty()) {
						db.syncData();
					}
					if (locked != null) {
						locks.remove(locked);
						locked = null;
					}
					Wire.line(out, "+OK Bye");
					return;
				}
				if (cmd.equals("CAPA")) {
					Wire.line(out, "+OK Capabilities");
					Wire.line(out, "USER");
					Wire.line(out, "UIDL");
					Wire.line(out, "XBOX");
					Wire.line(out, "XSAVE");
					Wire.line(out, "XCONTACTS");
					Wire.line(out, ".");
					continue;
				}
				if (cmd.equals("XREGISTER") && !auth) {
					try {
						String[] parts2 = arg.split(" ", 3);
						if (parts2.length < 2 || !parts2[0].matches("[A-Za-z0-9_-]{1,24}")) {
							throw new IllegalArgumentException();
						}
						String pass =
								new String(
										Base64.getDecoder().decode(parts2[1]),
										StandardCharsets.UTF_8);
						if (pass.isEmpty() || pass.length() > 128) {
							throw new IllegalArgumentException();
						}
						String name = parts2[0].toLowerCase(Locale.ROOT),
								email =
										parts2.length == 3
												? new String(
																Base64.getDecoder()
																		.decode(parts2[2]),
																StandardCharsets.UTF_8)
														.trim()
														.toLowerCase(Locale.ROOT)
												: name + "@localhost";
						if (!validEmail(email)) {
							throw new IllegalArgumentException();
						}
						Wire.line(
								out,
								db.register(name, pass, email)
										? "+OK Account registered"
										: "-ERR Username or email already exists");
					} catch (IllegalArgumentException exception) {
						Wire.line(out, "-ERR Invalid account details");
					}
					continue;
				}
				if (!auth) {
					if (cmd.equals("USER")) {
						user = arg.toLowerCase(Locale.ROOT);
						Wire.line(out, "+OK Password required");
					} else if (cmd.equals("PASS") && db.auth(user, arg)) {
						if (!locks.add(user)) {
							Wire.line(out, "-ERR Mailbox locked");
							continue;
						}
						locked = user;
						auth = true;
						sessionId =
								db.sessionStart(
										user, socket.getRemoteSocketAddress().toString(), "POP3");
						list = messages(user, false);
						Wire.line(out, "+OK Mailbox ready");
					} else {
						Wire.line(out, "-ERR USER then PASS required");
					}
					continue;
				}
				try {
					switch (cmd) {
						case "XCONTACTS" -> {
							List<Object[]> contacts =
									db.rows("SELECT name,email FROM users ORDER BY name");
							Wire.line(out, "+OK Local accounts");
							for (Object[] row : contacts) {
								String name = row[0].toString(),
										email =
												row[1] == null
														? name + "@localhost"
														: row[1].toString();
								Wire.line(
										out,
										Base64.getEncoder()
														.encodeToString(
																name.getBytes(
																		StandardCharsets.UTF_8))
												+ " "
												+ Base64.getEncoder()
														.encodeToString(
																email.getBytes(
																		StandardCharsets.UTF_8)));
							}
							Wire.line(out, ".");
						}
						case "XPROFILE" ->
								Wire.line(
										out,
										"+OK "
												+ Base64.getEncoder()
														.encodeToString(
																db.email(user)
																		.getBytes(
																				StandardCharsets
																						.UTF_8)));
						case "XSETEMAIL" -> {
							String email =
									new String(
													Base64.getDecoder().decode(arg),
													StandardCharsets.UTF_8)
											.trim()
											.toLowerCase(Locale.ROOT);
							if (!validEmail(email)) {
								Wire.line(out, "-ERR Invalid email");
								break;
							}
							try {
								db.exec("UPDATE users SET email=? WHERE name=?", email, user);
								Wire.line(out, "+OK Email updated");
							} catch (SQLException exception2) {
								Wire.line(out, "-ERR Email already registered");
							}
						}
						case "XBOX" -> {
							if (!deleted.isEmpty()) {
								Wire.line(out, "-ERR Commit deletions with QUIT first");
								break;
							}
							if (!arg.equalsIgnoreCase("INBOX") && !arg.equalsIgnoreCase("SENT")) {
								Wire.line(out, "-ERR Unknown mailbox");
								break;
							}
							sent = arg.equalsIgnoreCase("SENT");
							list = messages(user, sent);
							Wire.line(out, "+OK Mailbox selected");
						}
						case "XSAVE" -> {
							if (!arg.equalsIgnoreCase("SENT")) {
								Wire.line(out, "-ERR Use XSAVE SENT");
								break;
							}
							Wire.line(out, "+OK Send message, end with dot");
							byte[] body = readMessage(in);
							if (body == null) {
								Wire.line(out, "-ERR Message too large");
								break;
							}
							String from = header(body, "From"), to = header(body, "To");
							int begin = from.lastIndexOf('<'), end = from.lastIndexOf('>');
							if (begin >= 0 && end > begin) {
								from = from.substring(begin + 1, end).trim();
							}
							if (from.isBlank() || to.isBlank()) {
								Wire.line(out, "-ERR From and To required");
								break;
							}
							try (Connection connection2 = db.open()) {
								saveSent(
										connection2,
										user,
										from,
										to,
										body,
										List.of(to),
										socket.getInetAddress().getHostAddress(),
										true,
										Map.of());
							}
							db.syncData();
							Wire.line(out, "+OK Sent copy saved");
						}
						case "STAT" -> {
							long size = 0;
							for (int i = 0; i < list.size(); i++) {
								if (!deleted.contains(i + 1)) {
									size += list.get(i).body().length;
								}
							}
							Wire.line(out, "+OK " + (list.size() - deleted.size()) + " " + size);
						}
						case "NOOP" -> Wire.line(out, "+OK");
						case "RSET" -> {
							deleted.clear();
							Wire.line(out, "+OK Reset");
						}
						case "LIST", "UIDL" -> {
							if (arg.isBlank()) {
								Wire.line(out, "+OK Listing");
								for (int i = 0; i < list.size(); i++) {
									if (!deleted.contains(i + 1)) {
										Wire.line(
												out,
												""
														+ (i + 1)
														+ " "
														+ (cmd.equals("LIST")
																? list.get(i).body().length
																: list.get(i).uid()));
									}
								}
								Wire.line(out, ".");
							} else {
								int count2 = number(arg, list, deleted);
								Wire.line(
										out,
										"+OK "
												+ count2
												+ " "
												+ (cmd.equals("LIST")
														? list.get(count2 - 1).body().length
														: list.get(count2 - 1).uid()));
							}
						}
						case "RETR" -> {
							int count3 = number(arg, list, deleted);
							Wire.line(out, "+OK " + list.get(count3 - 1).body().length + " octets");
							String text =
									new String(list.get(count3 - 1).body(), StandardCharsets.UTF_8);
							if (!text.isEmpty()) {
								for (String row :
										text.substring(0, text.length() - 2).split("\r\n", -1)) {
									Wire.line(out, row.startsWith(".") ? "." + row : row);
								}
							}
							Wire.line(out, ".");
						}
						case "DELE" -> {
							deleted.add(number(arg, list, deleted));
							Wire.line(out, "+OK Marked");
						}
						default -> Wire.line(out, "-ERR Unknown command");
					}
				} catch (IllegalArgumentException exception3) {
					Wire.line(out, "-ERR No such message");
				}
			}
		} catch (Exception exception4) {
			System.err.println("POP3: " + exception4.getMessage());
		} finally {
			clients.remove(socket);
			if (locked != null) {
				locks.remove(locked);
			}
			try {
				db.sessionEnd(sessionId);
			} catch (Exception exception5) {
				System.err.println(exception5.getMessage());
			}
		}
	}

	private static int number(String arg, List<Message> list, Set<Integer> deleted) {
		int count = Integer.parseInt(arg);
		if (count < 1 || count > list.size() || deleted.contains(count)) {
			throw new IllegalArgumentException();
		}
		return count;
	}

	private static boolean validEmail(String email) {
		return email.length() <= 254 && email.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
	}

	public static void main(String[] arguments) throws Exception {
		new MailServer(
						Path.of(
								Wire.arg(
										arguments,
										2,
										System.getenv()
												.getOrDefault(
														"LAB_DB_CONFIG",
														"config/database.properties"))))
				.serve(
						Integer.parseInt(Wire.arg(arguments, 0, "2525")),
						Integer.parseInt(Wire.arg(arguments, 1, "2110")));
	}

	public void close() throws IOException {
		for (ServerSocket serverSocket : listeners) {
			serverSocket.close();
		}
		for (Socket socket : clients) {
			socket.close();
		}
	}
}
