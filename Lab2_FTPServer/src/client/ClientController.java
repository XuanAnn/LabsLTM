package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.nio.file.*;
import java.util.*;

/** Client logic separated from Swing, as in Lab 1. */
public final class ClientController implements AutoCloseable {
	public record Entry(String name, boolean directory, long size) {}

	private FtpClient client;

	public synchronized void login(ClientWindow.Login login) throws Exception {
		close();
		client = new FtpClient(login.host(), login.port(), login.user(), login.password());
	}

	public String register(ClientWindow.Login login) throws Exception {
		return FtpClient.register(login.host(), login.port(), login.user(), login.password());
	}

	public synchronized List<Entry> list(String directory) throws Exception {
		client.command("CWD " + directory, 250);
		List<Entry> result = new ArrayList<>();
		for (String line : client.list().split("\r\n")) {
			String[] parts = line.split("\\s+", 9);
			if (parts.length == 9) {
				result.add(new Entry(parts[8], parts[0].startsWith("d"), Long.parseLong(parts[4])));
			}
		}
		return result;
	}

	public synchronized void upload(String directory, Path file) throws Exception {
		client.command("CWD " + directory, 250);
		client.upload(file.getFileName().toString(), Files.readAllBytes(file));
	}

	public synchronized void download(String directory, String name, Path file) throws Exception {
		client.command("CWD " + directory, 250);
		byte[] bytes = client.download(name);
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, bytes);
	}

	public synchronized void mkdir(String directory, String name) throws Exception {
		client.command("CWD " + directory, 250);
		client.command("MKD " + name, 257);
	}

	public synchronized void delete(String directory, Entry entry) throws Exception {
		client.command("CWD " + directory, 250);
		client.command((entry.directory() ? "RMD " : "DELE ") + entry.name(), 250);
	}

	public String endpoint() {
		return client.localAddress();
	}

	public synchronized void close() {
		if (client != null) {
			try {
				client.close();
			} catch (Exception ignored) {
			}
		}
		client = null;
	}
}
