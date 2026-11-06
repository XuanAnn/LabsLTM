import client.*;
import server.*;
import common.*;
import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Integration tests use real processes/sockets and isolated persistent databases. */
public class IntegrationTest {
	static final List<Process> processes = new ArrayList<>();
	static Path dir;
	static Path mysqlConfig;
	static int checks;

	static void check(boolean ok, String label) {
		if (!ok) throw new AssertionError(label);
		checks++;
		System.out.println("PASS " + label);
	}

	static int port() throws Exception {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}

	static void until(BooleanSupplier condition) throws Exception {
		long end = System.nanoTime() + 12_000_000_000L;
		while (System.nanoTime() < end) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(100);
		}
		throw new AssertionError("Timed out");
	}

	static Process start(String clazz, String... args) throws Exception {
		List<String> cmd =
				new ArrayList<>(
						List.of(
								Path.of(System.getProperty("java.home"), "bin", "java.exe")
										.toString(),
								"-Dfile.encoding=UTF-8",
								"-cp",
								System.getProperty("java.class.path"),
								clazz));
		cmd.addAll(List.of(args));
		ProcessBuilder pb =
				new ProcessBuilder(cmd)
						.redirectErrorStream(true)
						.redirectOutput(
								dir.resolve(clazz + "-" + processes.size() + ".log").toFile());
		pb.environment().put("LAB_DB_CONFIG", mysqlConfig.toString());
		Process p = pb.start();
		processes.add(p);
		return p;
	}

	static void ready(int port) throws Exception {
		until(
				() -> {
					try (Socket s = Wire.connect("127.0.0.1", port)) {
						return true;
					} catch (Exception e) {
						return false;
					}
				});
	}

	static void stop(Process p) throws Exception {
		p.destroy();
		if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
			p.destroyForcibly();
			p.waitFor();
		}
	}

	static String raw(int port, String request) throws Exception {
		try (Socket s = Wire.connect("127.0.0.1", port)) {
			s.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
			s.getOutputStream().flush();
			return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	static void rmi() throws Exception {
		int ar = port(), ae = port(), br = port(), be = port();
		Path aConfig = TestDatabase.create(dir, "a"), bConfig = TestDatabase.create(dir, "b");
		Process a =
				start(
						"server.Node",
						"A",
						"" + ar,
						"" + ae,
						"127.0.0.1",
						aConfig.toString(),
						"127.0.0.1:" + br);
		Process b =
				start(
						"server.Node",
						"B",
						"" + br,
						"" + be,
						"127.0.0.1",
						bConfig.toString(),
						"127.0.0.1:" + ar);
		ready(ar);
		ready(br);
		Store as = Client.connect("localhost", ar), bs = Client.connect("localhost", br);
		as.put("student", "Alice", false);
		until(
				() -> {
					try {
						return bs.snapshot().containsKey("student");
					} catch (Exception e) {
						return false;
					}
				});
		check(
				bs.snapshot().get("student").text().equals("Alice"),
				"RMI A→B replication across processes");
		bs.put("student", "Bob", false);
		until(
				() -> {
					try {
						return as.snapshot().get("student").text().equals("Bob");
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "RMI B→A replication");
		stop(b);
		as.put("offline", "queued", false);
		b =
				start(
						"server.Node",
						"B",
						"" + br,
						"" + be,
						"127.0.0.1",
						bConfig.toString(),
						"127.0.0.1:" + ar);
		ready(br);
		Store recovered = Client.connect("localhost", br);
		until(
				() -> {
					try {
						return recovered.snapshot().containsKey("offline");
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "RMI restart persistence + catch-up after outage");
		as.put("student", "", true);
		until(
				() -> {
					try {
						return recovered.snapshot().get("student").deleted();
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "RMI deletion tombstone replicates");
		Store.Value va = new Store.Value("A", 100, "A", false),
				vb = new Store.Value("B", 100, "B", false);
		as.merge(Map.of("conflict", va));
		recovered.merge(Map.of("conflict", vb));
		until(
				() -> {
					try {
						return as.snapshot().get("conflict").text().equals("B")
								&& recovered.snapshot().get("conflict").text().equals("B");
					} catch (Exception e) {
						return false;
					}
				});
		check(true, "RMI deterministic equal-counter conflict resolution");
		Db aDb = new Db(aConfig), bDb = new Db(bConfig);
		check(!aDb.name().equals(bDb.name()), "RMI nodes use separate MySQL schemas");
		check(
				aDb.rows("SELECT entry_value FROM entries WHERE entry_key='conflict'")
								.getFirst()[0]
								.equals("B")
						&& bDb.rows("SELECT entry_value FROM entries WHERE entry_key='conflict'")
								.getFirst()[0]
								.equals("B"),
				"RMI convergence is visible in both MySQL databases");
	}

	static void lifecycle() throws Exception {
		ServerController controller = new ServerController();
		int first = port(), second = port();
		var settings =
				new common.AdminFrame.Settings(
						mysqlConfig, first, second, "Local", "127.0.0.1", "");
		try {
			controller.start(settings);
			ready(first);
			check(controller.running(), "Server dashboard controller starts service");
			controller.stop();
			until(
					() -> {
						try (Socket ignored = Wire.connect("localhost", first)) {
							return false;
						} catch (Exception e) {
							return true;
						}
					});
			check(!controller.running(), "Server dashboard controller releases service port");
			controller.start(settings);
			ready(first);
			check(controller.running(), "Server dashboard controller restarts on the same port");
		} finally {
			controller.stop();
		}
	}

	public static void main(String[] args) throws Exception {
		Files.createDirectories(Path.of("target"));
		dir = Files.createTempDirectory(Path.of("target"), "integration-").toAbsolutePath();
		System.out.println("Artifacts: " + dir);
		mysqlConfig = TestDatabase.create(dir, "main");
		System.setProperty("lab.db.config", mysqlConfig.toString());
		try {
			rmi();
			lifecycle();
			System.out.println("ALL " + checks + " CHECKS PASSED");
		} finally {
			for (Process p : processes) if (p.isAlive()) stop(p);
			TestDatabase.cleanup();
		}
	}
}
