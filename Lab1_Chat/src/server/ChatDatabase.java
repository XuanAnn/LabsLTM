package server;

import common.ChatRecord;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Chỉ server kết nối MySQL; tài khoản và nhóm vẫn dùng TXT. */
public final class ChatDatabase {
	private final String url;
	private final Properties credentials = new Properties();

	public static Path labDirectory() throws Exception {
		Path location = Path.of(ChatDatabase.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		for (Path folder = location.toAbsolutePath(); folder != null; folder = folder.getParent()) {
			if (Files.isRegularFile(folder.resolve("src/server/ServerController.java"))
					|| folder.getFileName() != null && folder.getFileName().toString().equals("Lab1_Chat")) {
				return folder;
			}
		}
		return Path.of("").toAbsolutePath();
	}

	public ChatDatabase() throws Exception {
		String override = System.getProperty("lab.db.config", System.getenv("LAB_DB_CONFIG"));
		Path config = override == null ? labDirectory().resolve("config/database.properties") : Path.of(override);
		Properties settings = new Properties();
		try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
			settings.load(reader);
		}
		String database = settings.getProperty("database", "ltm_chat");
		if (!database.matches("[A-Za-z0-9_]+")) throw new IOException("Tên database không hợp lệ");
		credentials.setProperty("user", settings.getProperty("user", "root"));
		credentials.setProperty("password", settings.getProperty("password", ""));
		Class.forName("com.mysql.cj.jdbc.Driver");
		String server = "jdbc:mysql://" + settings.getProperty("host", "127.0.0.1") + ":"
				+ settings.getProperty("port", "3306") + "/";
		String options = "?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=UTF-8";
		try (Connection connection = DriverManager.getConnection(server + options, credentials);
				Statement statement = connection.createStatement()) {
			statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "` CHARACTER SET utf8mb4");
		}
		url = server + database + options;
		try (Connection connection = open(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS chat_messages ("
					+ "id BIGINT AUTO_INCREMENT PRIMARY KEY,target_type VARCHAR(20) NOT NULL,"
					+ "sender VARCHAR(255) NOT NULL,target VARCHAR(255) NOT NULL,"
					+ "content LONGTEXT NOT NULL,sent_at BIGINT NOT NULL)");
		}
	}

	private Connection open() throws SQLException {
		return DriverManager.getConnection(url, credentials);
	}

	public void save(ChatRecord record) throws SQLException {
		String sql = "INSERT INTO chat_messages(target_type,sender,target,content,sent_at) VALUES(?,?,?,?,?)";
		try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, record.getTargetType().name());
			statement.setString(2, record.getSender());
			statement.setString(3, record.getTarget());
			statement.setString(4, record.getMessage());
			statement.setLong(5, record.getTimestamp());
			statement.executeUpdate();
		}
	}

	public List<ChatRecord> loadHistory() throws SQLException {
		List<ChatRecord> history = new ArrayList<>();
		try (Connection connection = open(); Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT target_type,sender,target,content,sent_at FROM chat_messages ORDER BY id")) {
			while (resultSet.next()) {
				history.add(new ChatRecord(ChatRecord.TargetType.valueOf(resultSet.getString(1)),
						resultSet.getString(2), resultSet.getString(3), resultSet.getString(4), resultSet.getLong(5)));
			}
		}
		return history;
	}
}
