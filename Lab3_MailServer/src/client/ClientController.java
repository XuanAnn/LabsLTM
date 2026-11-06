package client;

import client.*;
import server.*;
import common.*;

import common.*;
import common.ClientWindow;
import common.MailText;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;

public final class ClientController {
	public record Mail(
			int number,
			String from,
			String subject,
			String date,
			String body,
			String raw,
			String uid,
			String to,
			String folder,
			String senderIp,
			String recipientIp,
			String senderName,
			String recipientNames) {}

	public record Contact(String username, String email) {
		public String display() {
			return username + " <" + email + ">";
		}
	}

	private volatile ClientWindow.Login login;
	private volatile String email = "";

	@FunctionalInterface
	public interface GmailDelivery {
		void deliver(GmailSettings settings, String to, byte[] message) throws Exception;
	}

	private final GmailDelivery gmailDelivery;

	public ClientController() {
		this(
				(settings, to, message) ->
						MailClient.sendRaw(
								"smtp.gmail.com",
								587,
								true,
								settings.account(),
								settings.password(),
								settings.account(),
								to,
								message));
	}

	public ClientController(GmailDelivery delivery) {
		gmailDelivery = Objects.requireNonNull(delivery);
	}

	private static String ok(InputStream in) throws IOException {
		String line = Wire.line(in);
		if (line == null || !line.startsWith("+OK")) {
			throw new IOException(
					line == null ? "Mất kết nối máy chủ" : line.replaceFirst("^-ERR\\s*", ""));
		}
		return line;
	}

	private static String multi(InputStream in) throws IOException {
		StringBuilder textBuilder = new StringBuilder();
		while (true) {
			String row = Wire.line(in);
			if (row == null) {
				throw new EOFException();
			}
			if (row.equals(".")) {
				return textBuilder.toString();
			}
			textBuilder.append(row.startsWith("..") ? row.substring(1) : row).append('\n');
		}
	}

	private static String box(String folder) {
		if (!Set.of("INBOX", "SENT").contains(folder)) {
			throw new IllegalArgumentException("Thư mục không hợp lệ");
		}
		return folder;
	}

	private static final class Session implements AutoCloseable {
		final Socket socket;
		final InputStream in;
		final OutputStream out;

		Session(ClientWindow.Login login2) throws Exception {
			socket = Wire.connect(login2.host(), login2.extraPort());
			in = socket.getInputStream();
			out = socket.getOutputStream();
			try {
				ok(in);
				command("USER " + login2.user());
				command("PASS " + login2.password());
			} catch (Exception exception) {
				socket.close();
				throw exception;
			}
		}

		String command(String text) throws IOException {
			Wire.line(out, text);
			return ok(in);
		}

		public void close() throws IOException {
			socket.close();
		}

		void quit() throws IOException {
			command("QUIT");
		}
	}

	public synchronized void login(ClientWindow.Login details) throws Exception {
		try (Session session = new Session(details)) {
			String profile = session.command("XPROFILE");
			email =
					new String(
							Base64.getDecoder().decode(profile.substring(3).trim()),
							StandardCharsets.UTF_8);
			session.quit();
		}
		login = details;
	}

	private ClientWindow.Login account() throws IOException {
		ClientWindow.Login current = login;
		if (current == null) {
			throw new IOException("Hãy đăng nhập trước");
		}
		return current;
	}

	public String username() throws IOException {
		return account().user();
	}

	public String email() {
		return email;
	}

	public String displaySender() throws IOException {
		return username() + " <" + email + ">";
	}

	public synchronized List<Contact> contacts() throws Exception {
		try (Session session = new Session(account())) {
			session.command("XCONTACTS");
			List<Contact> result = new ArrayList<>();
			for (String row : multi(session.in).split("\n")) {
				if (row.isBlank()) {
					continue;
				}
				String[] fields = row.split(" ", 2);
				result.add(
						new Contact(
								new String(
										Base64.getDecoder().decode(fields[0]),
										StandardCharsets.UTF_8),
								new String(
										Base64.getDecoder().decode(fields[1]),
										StandardCharsets.UTF_8)));
			}
			session.quit();
			return result;
		}
	}

	public String register(ClientWindow.Login login2) throws Exception {
		return register(login2, login2.user() + "@localhost");
	}

	public String register(ClientWindow.Login login2, String address) throws Exception {
		return MailClient.register(
				login2.host(), login2.extraPort(), login2.user(), login2.password(), address);
	}

	public synchronized void updateEmail(String address) throws Exception {
		String normalized = address.trim().toLowerCase(Locale.ROOT);
		try (Session session = new Session(account())) {
			session.command(
					"XSETEMAIL "
							+ Base64.getEncoder()
									.encodeToString(normalized.getBytes(StandardCharsets.UTF_8)));
			session.quit();
		}
		email = normalized;
	}

	public List<Mail> inbox() throws Exception {
		return messages("INBOX");
	}

	public List<Mail> sent() throws Exception {
		return messages("SENT");
	}

	public synchronized List<Mail> messages(String folder) throws Exception {
		box(folder);
		try (Session session = new Session(account())) {
			if (folder.equals("SENT")) {
				session.command("XBOX SENT");
			}
			session.command("UIDL");
			String listing = multi(session.in);
			List<Mail> result = new ArrayList<>();
			for (String row : listing.split("\n")) {
				if (row.isBlank()) {
					continue;
				}
				String[] parts = row.split(" ", 2);
				int number = Integer.parseInt(parts[0]);
				session.command("RETR " + number);
				Mail decoded = decode(number, multi(session.in));
				result.add(
						new Mail(
								number,
								decoded.from(),
								decoded.subject(),
								decoded.date(),
								decoded.body(),
								decoded.raw(),
								parts[1],
								decoded.to(),
								folder,
								decoded.senderIp(),
								decoded.recipientIp(),
								decoded.senderName(),
								decoded.recipientNames()));
			}
			session.quit();
			Collections.reverse(result);
			return result;
		}
	}

	private static String decodeHeader(String value) {
		Matcher matcher =
				java.util.regex.Pattern.compile(
								"=\\?UTF-8\\?B\\?([^?]+)\\?=",
								java.util.regex.Pattern.CASE_INSENSITIVE)
						.matcher(value);
		StringBuffer decoded = new StringBuffer();
		while (matcher.find()) {
			try {
				matcher.appendReplacement(
						decoded,
						java.util.regex.Matcher.quoteReplacement(
								new String(
										Base64.getDecoder().decode(matcher.group(1)),
										StandardCharsets.UTF_8)));
			} catch (IllegalArgumentException exception) {
				matcher.appendReplacement(
						decoded, java.util.regex.Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(decoded);
		return decoded.toString();
	}

	public static Mail decode(int number, String original) {
		MailText.Content content = MailText.parse(original);
		String recipients = content.field("To");
		if (recipients.equals("all-local:;")) {
			recipients = "Tất cả tài khoản trên máy chủ";
		}
		String subject = content.field("Subject");
		if (subject.isBlank()) {
			subject = "(không tiêu đề)";
		}
		return new Mail(
				number,
				content.field("From"),
				subject,
				content.field("Date"),
				content.body(),
				content.raw(),
				"",
				recipients,
				"INBOX",
				MailText.ipOnly(content.field("X-Local-Sender-IP")),
				content.field("X-Local-Recipient-IP"),
				content.field("X-Local-Sender-Name"),
				content.field("X-Local-Recipient-Names"));
	}

	public void send(String to, String subject, String body) throws Exception {
		sendGroup(recipientAddresses(to), subject, body);
	}

	public static List<String> recipientAddresses(String input) {
		Set<String> result = new LinkedHashSet<>();
		for (String token : input.split("[,;]")) {
			String value = token.trim();
			if (value.isEmpty()) {
				continue;
			}
			int start = value.lastIndexOf('<'), end = value.lastIndexOf('>');
			if (start >= 0 && end > start) {
				value = value.substring(start + 1, end).trim();
			}
			result.add(value.toLowerCase(Locale.ROOT));
		}
		return new ArrayList<>(result);
	}

	public void sendGroup(List<String> recipients, String subject, String body) throws Exception {
		List<String> unique = recipientAddresses(String.join(",", recipients));
		if (unique.isEmpty()) {
			throw new IOException("Chọn ít nhất một người nhận");
		}
		ClientWindow.Login login2 = account();
		byte[] raw = MailClient.message(displaySender(), String.join(", ", unique), subject, body);
		MailClient.sendRawTo(
				login2.host(),
				login2.port(),
				false,
				login2.user(),
				login2.password(),
				email,
				unique,
				raw,
				false,
				false);
	}

	public void sendBroadcast(boolean includeSelf, String subject, String body) throws Exception {
		ClientWindow.Login login2 = account();
		byte[] raw = MailClient.message(displaySender(), "all-local:;", subject, body);
		MailClient.sendRawTo(
				login2.host(),
				login2.port(),
				false,
				login2.user(),
				login2.password(),
				email,
				List.of(),
				raw,
				true,
				includeSelf);
	}

	public synchronized void saveSent(byte[] raw) throws Exception {
		try (Session session = new Session(account())) {
			session.command("XSAVE SENT");
			MailClient.writeMessage(session.out, raw);
			ok(session.in);
			session.quit();
		}
	}

	public synchronized void sendGmail(String to, String subject, String body) throws Exception {
		sendGmail(to, subject, body, GmailSettings.load());
	}

	public synchronized void sendGmail(
			String to, String subject, String body, GmailSettings settings) throws Exception {
		if (settings.account().isBlank() || settings.password().isBlank()) {
			throw new IOException("Hãy cấu hình tài khoản Gmail trước");
		}
		byte[] raw =
				MailClient.message(username() + " <" + settings.account() + ">", to, subject, body);
		// Sign in locally first so the sent copy can be stored immediately after external
		// acceptance.
		try (Session session = new Session(account())) {
			gmailDelivery.deliver(settings, to, raw);
			try {
				session.command("XSAVE SENT");
				MailClient.writeMessage(session.out, raw);
				ok(session.in);
				session.quit();
			} catch (Exception exception) {
				throw new IOException(
						"Thư đã gửi, nhưng chưa lưu được vào Đã gửi. Không gửi lại để tránh trùng"
								+ " thư.",
						exception);
			}
		}
	}

	public synchronized void delete(String folder, String uid) throws Exception {
		box(folder);
		try (Session session = new Session(account())) {
			if (folder.equals("SENT")) {
				session.command("XBOX SENT");
			}
			session.command("UIDL");
			String listing = multi(session.in);
			String number = null;
			for (String row : listing.split("\n")) {
				String[] fields = row.split(" ", 2);
				if (fields.length == 2 && fields[1].equals(uid)) {
					number = fields[0];
					break;
				}
			}
			if (number == null) {
				session.quit();
				throw new IOException("Thư không còn trong hộp thư. Hãy làm mới danh sách.");
			}
			session.command("DELE " + number);
			session.quit();
		}
	}

	public synchronized void delete(int number) throws Exception {
		try (Session session = new Session(account())) {
			session.command("DELE " + number);
			session.quit();
		}
	}
}
