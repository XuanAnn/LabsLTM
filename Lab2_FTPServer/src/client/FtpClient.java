package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import javax.swing.*;

public final class FtpClient implements AutoCloseable {
	private final Socket socket;
	private final InputStream in;
	private final OutputStream out;

	public FtpClient(String host, int port, String user, String password) throws Exception {
		socket = Wire.connect(host, port);
		in = socket.getInputStream();
		out = socket.getOutputStream();
		try {
			expect(220);
			command("USER " + user, 331);
			command("PASS " + password, 230);
			command("TYPE I", 200);
		} catch (Exception exception) {
			socket.close();
			throw exception;
		}
	}

	private String expect(int code) throws IOException {
		String text = Wire.line(in);
		if (text == null || !text.startsWith("" + code)) {
			throw new IOException("FTP: " + text);
		}
		return text;
	}

	public String command(String text, int code) throws IOException {
		Wire.line(out, text);
		return expect(code);
	}

	private Socket data() throws IOException {
		String text = command("EPSV", 229);
		String[] parts = text.substring(text.indexOf('(') + 1, text.indexOf(')')).split("\\|");
		return Wire.connect(socket.getInetAddress().getHostAddress(), Integer.parseInt(parts[3]));
	}

	public byte[] download(String name) throws Exception {
		try (Socket d = data()) {
			command("RETR " + name, 150);
			byte[] bytes = d.getInputStream().readAllBytes();
			expect(226);
			return bytes;
		}
	}

	public void upload(String name, byte[] bytes) throws Exception {
		try (Socket d = data()) {
			command("STOR " + name, 150);
			d.getOutputStream().write(bytes);
			d.shutdownOutput();
			expect(226);
		}
	}

	public String list() throws Exception {
		try (Socket d = data()) {
			command("LIST", 150);
			String text = new String(d.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			expect(226);
			return text;
		}
	}

	public void close() throws IOException {
		socket.close();
	}

	public String localAddress() {
		return socket.getLocalAddress().getHostAddress();
	}

	public static String register(String host, int port, String user, String password)
			throws Exception {
		try (Socket socket2 = Wire.connect(host, port)) {
			InputStream in = socket2.getInputStream();
			OutputStream out = socket2.getOutputStream();
			Wire.line(in);
			Wire.line(
					out,
					"SITE REGISTER "
							+ user
							+ " "
							+ java.util.Base64.getEncoder()
									.encodeToString(password.getBytes(StandardCharsets.UTF_8)));
			String result = Wire.line(in);
			if (result == null || !result.startsWith("200")) {
				throw new IOException(result);
			}
			return result;
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(
				() -> {
					Desk ui = new Desk("Lab 2 • FTP client — TCP control + passive data");
					ui.field("Host", "localhost");
					ui.field("Port", "2121");
					ui.field("User", "student");
					ui.field("Password", "student123", true);
					ui.field("Remote file/path", "hello.txt");
					ui.field("Local file", "downloads/hello.txt");
					ui.button(
							"Đăng ký",
							() ->
									register(
											ui.get("Host"),
											Integer.parseInt(ui.get("Port")),
											ui.get("User"),
											ui.get("Password")));
					ui.button(
							"Đăng nhập",
							() -> {
								try (FtpClient ftpClient = connect(ui)) {
									return "Đăng nhập thành công: " + ui.get("User");
								}
							});
					ui.button(
							"List",
							() -> {
								try (FtpClient ftpClient2 = connect(ui)) {
									return ftpClient2.list();
								}
							});
					ui.button(
							"Upload",
							() -> {
								try (FtpClient ftpClient3 = connect(ui)) {
									ftpClient3.upload(
											ui.get("Remote file/path"),
											Files.readAllBytes(Path.of(ui.get("Local file"))));
									return "Upload complete";
								}
							});
					ui.button(
							"Download",
							() -> {
								try (FtpClient ftpClient4 = connect(ui)) {
									byte[] bytes = ftpClient4.download(ui.get("Remote file/path"));
									Path filePath = Path.of(ui.get("Local file"));
									Files.createDirectories(filePath.toAbsolutePath().getParent());
									Files.write(filePath, bytes);
									return "Saved " + bytes.length + " bytes: " + filePath;
								}
							});
					ui.button(
							"Make directory",
							() -> {
								try (FtpClient ftpClient5 = connect(ui)) {
									return ftpClient5.command(
											"MKD " + ui.get("Remote file/path"), 257);
								}
							});
					ui.button(
							"Delete file",
							() -> {
								try (FtpClient ftpClient6 = connect(ui)) {
									return ftpClient6.command(
											"DELE " + ui.get("Remote file/path"), 250);
								}
							});
					ui.show();
				});
	}

	private static FtpClient connect(Desk ui) throws Exception {
		return new FtpClient(
				ui.get("Host"),
				Integer.parseInt(ui.get("Port")),
				ui.get("User"),
				ui.get("Password"));
	}
}
