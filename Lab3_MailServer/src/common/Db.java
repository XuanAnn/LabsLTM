package common;

import client.*;
import server.*;
import common.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Local MySQL configuration for this lab only; no embedded DB or cross-lab dependency. */
public final class Db {
	private final String url;
	private final Properties credentials = new Properties();
	private final String database;
	private final Path dataDirectory;

	public Db() throws Exception {
		this(defaultConfigPath());
	}

	public Db(Path config) throws Exception {
		// Bước 1: đọc địa chỉ, tên database và tài khoản từ file cấu hình của lab.
		Properties settings = readSettings(config);
		dataDirectory =
				LabPaths.resolve(Path.of(settings.getProperty("data.directory", "server/data")))
						.toAbsolutePath()
						.normalize();
		database = settings.getProperty("database");
		if (database == null || !database.matches("[A-Za-z0-9_]+")) {
			throw new IOException("Invalid database in " + config);
		}

		// Bước 2: ghép URL JDBC, sau đó chuẩn bị tài khoản để mở Connection.
		Class.forName("com.mysql.cj.jdbc.Driver");
		String host = settings.getProperty("host", "127.0.0.1");
		String port = settings.getProperty("port", "3306");
		String address = "jdbc:mysql://" + host + ":" + port + "/" + database;
		String options =
				"?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=LOCAL&connectTimeout=5000&socketTimeout=15000";
		url = address + options;
		credentials.setProperty("user", settings.getProperty("user", "root"));
		credentials.setProperty("password", settings.getProperty("password", ""));

		// Bước 3: tạo/nâng cấp bảng trước khi nhận các thao tác từ client.
		initializeSchema();

		syncFiles();
	}

	private static Path defaultConfigPath() {
		String filename = System.getProperty("lab.db.config");
		if (filename == null) {
			filename = System.getenv("LAB_DB_CONFIG");
		}
		if (filename == null) {
			filename = "config/database.properties";
		}
		return LabPaths.resolve(Path.of(filename));
	}

	private static Properties readSettings(Path config) throws IOException {
		Properties settings = new Properties();
		try (Reader reader = Files.newBufferedReader(LabPaths.resolve(config), StandardCharsets.UTF_8)) {
			settings.load(reader);
		}
		return settings;
	}

	private void initializeSchema() throws Exception {
		try (InputStream in = Db.class.getResourceAsStream("/schema.sql")) {
			if (in == null) {
				throw new IOException("schema.sql missing from jar");
			}
			String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			try (Connection connection = open();
					Statement statement = connection.createStatement()) {
				for (String sql : schema.split(";")) {
					if (sql.isBlank()) {
						continue;
					}
					String command = sql.stripLeading().toUpperCase(Locale.ROOT);
					if (command.startsWith("INSERT IGNORE INTO USERS")
							&& !SaveOptions.enabled(SaveOptions.Kind.ACCOUNT)) {
						continue;
					}
					// Khi tắt bản gửi, hoãn cả backfill và mốc migration để có thể bật lại sau.
					boolean sentMigration =
							command.startsWith("INSERT IGNORE INTO SENT_MAIL")
									|| command.startsWith(
											"INSERT IGNORE INTO MAIL_SCHEMA_MIGRATIONS");
					if (sentMigration && !SaveOptions.enabled(SaveOptions.Kind.MAIL_SENT)) {
						continue;
					}
					statement.executeUpdate(sql);
				}
			}
		}
	}

	public Connection open() throws SQLException {
		return DriverManager.getConnection(url, credentials);
	}

	public String name() {
		return database;
	}

	/**
	 * Thực thi một câu INSERT / UPDATE / DELETE bằng JDBC. Mỗi dấu ? nhận một giá trị theo đúng thứ
	 * tự, bắt đầu từ vị trí 1. Kết nối này dùng auto-commit; phần transaction nhiều bản ghi được
	 * viết riêng tại nơi gọi.
	 */
	public void exec(String sql, Object... values) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < values.length; index++) {
				statement.setObject(index + 1, values[index]);
			}
			statement.executeUpdate();
		}

		if (sql.matches(
				"(?is)^\\s*(?:INSERT(?:\\s+IGNORE)?\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(users|mail|sent_mail)\\b.*")) {
			syncData();
		}
	}

	public Path dataDirectory() {
		return dataDirectory;
	}

	public void syncFiles() throws Exception {
		if (!SaveOptions.enabled(SaveOptions.Kind.TEXT_EXPORT)) {
			return;
		}
		DataFiles.sync(this, dataDirectory);
	}

	public void syncData() {
		try {
			syncFiles();
		} catch (Exception exception) {
			System.err.println(
					"Database updated; server files will be refreshed on restart: "
							+ exception.getMessage());
		}
	}

	/** Đọc SELECT: duyệt từng dòng ResultSet, không thêm bản ghi dữ liệu. */
	public List<Object[]> rows(String sql, Object... values) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < values.length; index++) {
				statement.setObject(index + 1, values[index]);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				List<Object[]> results = new ArrayList<>();
				int columnCount = resultSet.getMetaData().getColumnCount();
				while (resultSet.next()) {
					Object[] row = new Object[columnCount];
					for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
						row[columnIndex] = resultSet.getObject(columnIndex + 1);
					}
					results.add(row);
				}
				return results;
			}
		}
	}

	public long count(String table) throws SQLException {
		if (!table.matches("[a-z_]+")) {
			throw new IllegalArgumentException();
		}
		return ((Number) rows("SELECT COUNT(*) FROM " + table).getFirst()[0]).longValue();
	}

	public void audit(String event) throws SQLException {
		if (SaveOptions.enabled(SaveOptions.Kind.AUDIT)) {
			exec("INSERT INTO audit(event) VALUES(?)", event);
		}
		System.out.println(event);
	}

	public void user(String name, String password) throws SQLException {
		if (!SaveOptions.enabled(SaveOptions.Kind.ACCOUNT)) {
			return;
		}
		exec(
				"INSERT IGNORE INTO users(name,password,email) VALUES(?,?,?)",
				name,
				password,
				name + "@localhost");
	}

	public boolean register(String name, String password) throws SQLException {
		return register(name, password, name + "@localhost");
	}

	public boolean register(String name, String password, String email) throws SQLException {
		if (!SaveOptions.enabled(SaveOptions.Kind.ACCOUNT)) {
			throw new SQLException("Đang tắt lưu tài khoản mới trong SaveOptions");
		}
		if (!name.matches("[A-Za-z0-9_-]{1,24}")
				|| password.isEmpty()
				|| password.length() > 128
				|| email.length() > 254
				|| !email.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+")) {
			throw new SQLException("Nhập tên tài khoản, email và mật khẩu hợp lệ");
		}
		boolean created;
		try (Connection connection = open();
				PreparedStatement statement =
						connection.prepareStatement(
								"INSERT IGNORE INTO users(name,password,email) VALUES(?,?,?)")) {
			statement.setString(1, name);
			statement.setString(2, password);
			statement.setString(3, email);
			created = statement.executeUpdate() == 1;
		}
		if (created) {
			syncData();
		}
		return created;
	}

	public String email(String user) throws SQLException {
		List<Object[]> result = rows("SELECT email FROM users WHERE name=?", user);
		return result.isEmpty() || result.getFirst()[0] == null
				? user + "@localhost"
				: result.getFirst()[0].toString();
	}

	public boolean auth(String name, String password) throws SQLException {
		return !rows(
						"SELECT name FROM users WHERE name=? AND BINARY password=BINARY ?",
						name,
						password)
				.isEmpty();
	}

	public String sessionStart(String user, String peer, String protocol) throws SQLException {
		if (!SaveOptions.enabled(SaveOptions.Kind.SESSION)) {
			return null;
		}
		String id = UUID.randomUUID().toString();
		exec(
				"INSERT INTO sessions(id,username,remote_ip,protocol) VALUES(?,?,?,?)",
				id,
				user,
				peer,
				protocol);
		exec(
				"UPDATE users SET last_login=NOW(),last_ip=? WHERE name=?",
				MailText.ipOnly(peer),
				user);
		audit("LOGIN " + protocol + " " + user + " " + peer);
		return id;
	}

	public void sessionEnd(String id) throws SQLException {
		if (id == null) {
			return;
		}
		exec(
				"UPDATE users u JOIN sessions s ON s.username=u.name SET u.last_logout=NOW() WHERE"
						+ " s.id=?",
				id);
		exec("UPDATE sessions SET disconnected_at=NOW() WHERE id=?", id);
	}
}
