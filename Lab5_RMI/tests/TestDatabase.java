import common.Db;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Each run creates unique MySQL test schemas, independent from the demo schemas. */
public final class TestDatabase {
	private static final java.util.List<Path> configs = new ArrayList<>();

	public static Path create(Path folder, String suffix) throws Exception {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(Path.of("config/database.properties"))) {
			p.load(r);
		}
		p.setProperty(
				"host", System.getenv().getOrDefault("TEST_MYSQL_HOST", p.getProperty("host")));
		p.setProperty(
				"port", System.getenv().getOrDefault("TEST_MYSQL_PORT", p.getProperty("port")));
		p.setProperty(
				"user", System.getenv().getOrDefault("TEST_MYSQL_USER", p.getProperty("user")));
		p.setProperty(
				"password",
				System.getenv().getOrDefault("TEST_MYSQL_PASSWORD", p.getProperty("password", "")));
		p.setProperty(
				"database",
				"ltm_test_rmi_"
						+ suffix
						+ "_"
						+ UUID.randomUUID().toString().replace("-", "").substring(0, 8));
		Path config = folder.resolve("mysql-" + suffix + ".properties");
		try (Writer w = Files.newBufferedWriter(config)) {
			p.store(w, "Temporary integration schema");
		}
		configs.add(config);
		new Db(config);
		return config;
	}

	public static void cleanup() throws Exception {
		if ("1".equals(System.getenv("TEST_KEEP_DB"))) return;
		for (Path path : configs) {
			Db db = new Db(path);
			if (!db.name().matches("ltm_test_rmi_[a-z]+_[0-9a-f]{8}"))
				throw new IllegalStateException("Unexpected test schema");
			db.exec("DROP DATABASE " + db.name());
		}
	}
}
