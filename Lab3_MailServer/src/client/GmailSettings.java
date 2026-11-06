package client;

import client.*;
import server.*;
import common.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Machine-local account settings, excluded from version control. */
public final class GmailSettings {
	public static Path file() throws IOException {
		String override = System.getProperty("lab.gmail.config");
		if (override != null && !override.isBlank()) {
			return Path.of(override).toAbsolutePath().normalize();
		}
		try {
			Path classes = Path.of(GmailSettings.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI());
			return resolveFile(Path.of(""), classes);
		} catch (java.net.URISyntaxException exception) {
			throw new IOException("Không xác định được thư mục cài đặt gửi thư", exception);
		}
	}

	public static Path resolveFile(Path workingDirectory, Path classes) {
		// Jar và target/classes đều thuộc thư mục lab, không phụ thuộc Working directory của IDE.
		for (Path folder = classes.toAbsolutePath().normalize(); folder != null; folder = folder.getParent()) {
			if (Files.isRegularFile(folder.resolve("src/client/GmailSettings.java"))
					|| Files.isRegularFile(folder.resolve("config/gmail.local.properties"))) {
				return folder.resolve("config/gmail.local.properties");
			}
		}
		Path working = workingDirectory.toAbsolutePath().normalize();
		Path lab = working.resolve("Lab3_MailServer");
		if (Files.isDirectory(lab)) {
			return lab.resolve("config/gmail.local.properties");
		}
		return working.resolve("config/gmail.local.properties");
	}

	public boolean configured() {
		return !account.isBlank() && !password.isBlank();
	}
	private final String account, password;

	public GmailSettings(String account, String password) {
		this.account = account.trim();
		this.password = password.replaceAll("\\s", "");
	}

	public String account() {
		return account;
	}

	public String password() {
		return password;
	}

	public static GmailSettings load() throws IOException {
		Path file = file();
		Properties settings = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				settings.load(reader);
			}
		}
		return new GmailSettings(
				settings.getProperty("account", System.getenv().getOrDefault("GMAIL_USER", "")),
				settings.getProperty(
						"password", System.getenv().getOrDefault("GMAIL_APP_PASSWORD", "")));
	}

	public void save() throws IOException {
		Path file = file();
		Properties settings = new Properties();
		settings.setProperty("account", account);
		settings.setProperty("password", password);
		Files.createDirectories(file.getParent());
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			settings.store(writer, "Local mail account");
		}
	}

	public static void main(String[] args) throws Exception {
		GmailSettings gmailSettings = load();
		MailClient.verifyGmail(gmailSettings.account(), gmailSettings.password());
		System.out.println("Gmail account sign-in succeeded. No email was sent.");
	}
}
