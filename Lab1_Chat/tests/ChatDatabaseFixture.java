import java.nio.file.*;
import java.io.*;
import java.sql.*;
import java.util.*;

public final class ChatDatabaseFixture implements AutoCloseable {
	private final Properties settings = new Properties();
	private final String schema = "ltm_test_chat_" + UUID.randomUUID().toString().replace("-", "");
	private final String original = System.getProperty("lab.db.config");

	public ChatDatabaseFixture(Path folder) throws Exception {
		try (Reader reader = Files.newBufferedReader(Path.of("config/database.properties"))) {
			settings.load(reader);
		}
		for (String key : new String[] {"host", "port", "user", "password"}) {
			String override = System.getenv("TEST_MYSQL_" + key.toUpperCase(Locale.ROOT));
			if (override != null) settings.setProperty(key, override);
		}
		settings.setProperty("database", schema);
		Path config = folder.resolve("database.properties").toAbsolutePath();
		try (Writer writer = Files.newBufferedWriter(config)) { settings.store(writer, "Isolated test schema"); }
		System.setProperty("lab.db.config", config.toString());
	}

	public void close() throws Exception {
		if (original == null) System.clearProperty("lab.db.config");
		else System.setProperty("lab.db.config", original);
		if ("1".equals(System.getenv("TEST_KEEP_DB"))) return;
		if (!schema.matches("ltm_test_chat_[a-f0-9]{32}")) throw new IllegalStateException("Invalid test schema");
		String url = "jdbc:mysql://" + settings.getProperty("host") + ":" + settings.getProperty("port")
				+ "/?allowPublicKeyRetrieval=true&useSSL=false";
		try (Connection connection = DriverManager.getConnection(url, settings.getProperty("user"), settings.getProperty("password", ""));
				Statement statement = connection.createStatement()) {
			statement.executeUpdate("DROP DATABASE IF EXISTS `" + schema + "`");
		}
	}
}
