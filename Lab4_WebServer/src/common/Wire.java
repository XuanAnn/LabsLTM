package common;

import client.*;
import server.*;
import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Byte-oriented CRLF framing: never mix a buffered Reader with binary bodies. */
public final class Wire {
	private Wire() {}

	public static String line(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		for (int count; (count = in.read()) != -1; ) {
			if (count == '\n') {
				return buffer.toString(StandardCharsets.UTF_8).replaceFirst("\\r$", "");
			}
			if (buffer.size() >= 8192) {
				throw new IOException("Line exceeds 8192 bytes");
			}
			buffer.write(count);
		}
		if (buffer.size() != 0) {
			throw new EOFException("Truncated line");
		}
		return null;
	}

	public static void line(OutputStream out, String text) throws IOException {
		if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
			throw new IOException("Invalid line");
		}
		out.write((text + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

	public static byte[] bytes(InputStream in, int count) throws IOException {
		if (count < 0 || count > 32 * 1024 * 1024) {
			throw new IOException("Payload limit 32 MiB");
		}
		byte[] bytes = in.readNBytes(count);
		if (bytes.length != count) {
			throw new EOFException();
		}
		return bytes;
	}

	public static Socket connect(String host, int port) throws IOException {
		Socket socket = new Socket();
		socket.connect(new InetSocketAddress(host, port), 5000);
		socket.setSoTimeout(15000);
		return socket;
	}

	public static Path inside(Path root, String name) throws IOException {
		root = root.toAbsolutePath().normalize();
		Path filePath = root.resolve(name).normalize();
		if (!filePath.startsWith(root)) {
			throw new IOException("Path outside root");
		}
		for (Path filePath2 = filePath;
				filePath2 != null && filePath2.startsWith(root);
				filePath2 = filePath2.getParent()) {
			if (Files.isSymbolicLink(filePath2)) {
				throw new IOException("Symbolic links are forbidden");
			}
		}
		return filePath;
	}

	public static String hash(String text) {
		try {
			return HexFormat.of()
					.formatHex(
							MessageDigest.getInstance("SHA-256")
									.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static String arg(String[] arguments, int count, String fallback) {
		return arguments.length > count ? arguments[count] : fallback;
	}
}
