package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.net.ssl.*;

/** SMTP and POP3 framing is implemented directly on sockets. */
public final class MailClient {
	private static void reply(InputStream in, int code) throws IOException {
		String line = Wire.line(in);
		if (line == null || !line.startsWith("" + code)) {
			throw new IOException("Máy chủ từ chối yêu cầu: " + line);
		}
		while (line.length() > 3 && line.charAt(3) == '-') {
			line = Wire.line(in);
			if (line == null || !line.startsWith("" + code)) {
				throw new IOException("Phản hồi chưa đầy đủ");
			}
		}
	}

	private static void command(InputStream in, OutputStream out, String text, int code)
			throws IOException {
		Wire.line(out, text);
		reply(in, code);
	}

	private static final class Session implements AutoCloseable {
		private final Socket plain;
		private Socket secure;
		private InputStream in;
		private OutputStream out;

		Session(String host, int port, boolean tls, String user, String password) throws Exception {
			plain = Wire.connect(host, port);
			try {
				in = plain.getInputStream();
				out = plain.getOutputStream();
				reply(in, 220);
				command(in, out, "EHLO localhost", 250);
				if (tls) {
					command(in, out, "STARTTLS", 220);
					SSLSocket ssl =
							(SSLSocket)
									((SSLSocketFactory) SSLSocketFactory.getDefault())
											.createSocket(plain, host, port, false);
					secure = ssl;
					SSLParameters parameters = ssl.getSSLParameters();
					parameters.setEndpointIdentificationAlgorithm("HTTPS");
					ssl.setSSLParameters(parameters);
					ssl.setSoTimeout(15000);
					ssl.startHandshake();
					in = ssl.getInputStream();
					out = ssl.getOutputStream();
					command(in, out, "EHLO localhost", 250);
				}
				if (!user.isBlank()) {
					command(in, out, "AUTH LOGIN", 334);
					command(
							in,
							out,
							Base64.getEncoder()
									.encodeToString(user.getBytes(StandardCharsets.UTF_8)),
							334);
					command(
							in,
							out,
							Base64.getEncoder()
									.encodeToString(password.getBytes(StandardCharsets.UTF_8)),
							235);
				}
			} catch (Exception exception) {
				close();
				throw exception;
			}
		}

		public void close() throws IOException {
			try {
				if (secure != null) {
					secure.close();
				}
			} finally {
				plain.close();
			}
		}

		void quit() {
			try {
				command(in, out, "QUIT", 221);
			} catch (IOException ignored) {
				/* DATA was already acknowledged; do not resend. */
			}
		}
	}

	public static byte[] message(String from, String to, String subject, String body)
			throws IOException {
		for (String value : List.of(from, to, subject)) {
			if (value.contains("\r") || value.contains("\n")) {
				throw new IOException("Thông tin người gửi/nhận hoặc tiêu đề không hợp lệ");
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.line(out, "From: " + from);
		StringBuilder recipients = new StringBuilder("To: ");
		for (String address : to.split(",")) {
			if (recipients.length() > 4) {
				recipients.append(", ");
			}
			if (recipients.length() + address.length() > 900) {
				Wire.line(out, recipients.toString());
				recipients = new StringBuilder(" ");
			}
			recipients.append(address.trim());
		}
		Wire.line(out, recipients.toString());
		Wire.line(
				out,
				"Subject: =?UTF-8?B?"
						+ Base64.getEncoder()
								.encodeToString(subject.getBytes(StandardCharsets.UTF_8))
						+ "?=");
		Wire.line(out, "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
		Wire.line(out, "Message-ID: <" + UUID.randomUUID() + "@localhost>");
		Wire.line(out, "MIME-Version: 1.0");
		Wire.line(out, "Content-Type: text/plain; charset=UTF-8");
		Wire.line(out, "Content-Transfer-Encoding: base64");
		Wire.line(out, "");
		for (String row :
				Base64.getMimeEncoder(76, new byte[] {13, 10})
						.encodeToString(body.getBytes(StandardCharsets.UTF_8))
						.split("\r\n", -1)) {
			Wire.line(out, row);
		}
		return out.toByteArray();
	}

	public static void writeMessage(OutputStream out, byte[] message) throws IOException {
		String text = new String(message, StandardCharsets.UTF_8).replace("\r\n", "\n");
		if (text.endsWith("\n")) {
			text = text.substring(0, text.length() - 1);
		}
		for (String row : text.split("\n", -1)) {
			Wire.line(out, row.startsWith(".") ? "." + row : row);
		}
		Wire.line(out, ".");
	}

	public static void send(
			String host,
			int port,
			boolean tls,
			String user,
			String pass,
			String from,
			String to,
			String subject,
			String body)
			throws Exception {
		sendRaw(host, port, tls, user, pass, from, to, message(from, to, subject, body));
	}

	public static void sendRaw(
			String host,
			int port,
			boolean tls,
			String user,
			String pass,
			String from,
			String to,
			byte[] message)
			throws Exception {
		sendRawTo(host, port, tls, user, pass, from, List.of(to), message, false, false);
	}

	public static void sendRawTo(
			String host,
			int port,
			boolean tls,
			String user,
			String pass,
			String from,
			List<String> addresses,
			byte[] message,
			boolean broadcast,
			boolean includeSelf)
			throws Exception {
		if (!from.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+")) {
			throw new IOException("Địa chỉ gửi không hợp lệ");
		}
		Set<String> recipients = new LinkedHashSet<>();
		for (String address : addresses) {
			String normalized = address.trim().toLowerCase(Locale.ROOT);
			if (!normalized.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+")) {
				throw new IOException("Địa chỉ người nhận không hợp lệ: " + address);
			}
			recipients.add(normalized);
		}
		if (!broadcast && recipients.isEmpty()) {
			throw new IOException("Chọn ít nhất một người nhận");
		}
		Session session = new Session(host, port, tls, user, pass);
		boolean accepted = false;
		try {
			command(session.in, session.out, "MAIL FROM:<" + from + ">", 250);
			if (broadcast) {
				command(session.in, session.out, includeSelf ? "XRCPT ALL SELF" : "XRCPT ALL", 250);
			} else {
				for (String recipient : recipients) {
					command(session.in, session.out, "RCPT TO:<" + recipient + ">", 250);
				}
			}
			command(session.in, session.out, "DATA", 354);
			writeMessage(session.out, message);
			reply(session.in, 250);
			accepted = true;
			session.quit();
		} finally {
			try {
				session.close();
			} catch (IOException closeError) {
				if (!accepted) {
					throw closeError;
				}
			}
		}
	}

	public static void verifyGmail(String user, String password) throws Exception {
		if (user.isBlank() || password.isBlank()) {
			throw new IOException("Chưa cấu hình tài khoản gửi");
		}
		try (Session session =
				new Session("smtp.gmail.com", 587, true, user, password.replaceAll("\\s", ""))) {
			command(session.in, session.out, "NOOP", 250);
			session.quit();
		}
	}

	public static String inbox(String host, int port, String user, String pass, int retrieve)
			throws Exception {
		try (Socket socket = Wire.connect(host, port)) {
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			popReply(in);
			Wire.line(out, "USER " + user);
			popReply(in);
			Wire.line(out, "PASS " + pass);
			popReply(in);
			Wire.line(out, retrieve > 0 ? "RETR " + retrieve : "LIST");
			StringBuilder text = new StringBuilder(popReply(in) + "\n");
			for (String row; (row = Wire.line(in)) != null && !row.equals("."); ) {
				text.append(row.startsWith("..") ? row.substring(1) : row).append('\n');
			}
			Wire.line(out, "QUIT");
			popReply(in);
			return text.toString();
		}
	}

	private static String popReply(InputStream in) throws IOException {
		String line = Wire.line(in);
		if (line == null || !line.startsWith("+OK")) {
			throw new IOException(line);
		}
		return line;
	}

	public static String register(String host, int port, String user, String password)
			throws Exception {
		return register(host, port, user, password, user + "@localhost");
	}

	public static String register(String host, int port, String user, String password, String email)
			throws Exception {
		try (Socket socket = Wire.connect(host, port)) {
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			popReply(in);
			Wire.line(
					out,
					"XREGISTER "
							+ user
							+ " "
							+ Base64.getEncoder()
									.encodeToString(password.getBytes(StandardCharsets.UTF_8))
							+ " "
							+ Base64.getEncoder()
									.encodeToString(email.getBytes(StandardCharsets.UTF_8)));
			return popReply(in);
		}
	}

	public static void main(String[] args) {
		ClientUI.main(args);
	}
}
