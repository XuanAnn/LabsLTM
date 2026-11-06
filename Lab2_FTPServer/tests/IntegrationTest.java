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

	static void ftp() throws Exception {
		int port = port();
		Process server = start("server.FtpServer", "" + port, dir.resolve("ftp").toString());
		ready(port);
		byte[] blob = new byte[65537];
		new Random(42).nextBytes(blob);
		try (FtpClient c = new FtpClient("localhost", port, "student", "student123")) {
			c.upload("binary.bin", blob);
			check(
					Arrays.equals(blob, c.download("binary.bin")),
					"FTP binary upload/download exact bytes");
			check(c.list().contains("binary.bin"), "FTP LIST via separate data channel");
			c.command("MKD sub", 257);
			c.command("CWD sub", 250);
			c.upload("test.txt", "hello".getBytes());
			c.command("CWD /", 250);
			check(
					new String(c.download("sub/test.txt")).equals("hello"),
					"FTP CWD and nested file");
			boolean denied = false;
			try {
				c.command("CWD ../../", 250);
			} catch (IOException e) {
				denied = true;
			}
			check(denied, "FTP rejects traversal");
		}
		boolean denied = false;
		try (FtpClient c = new FtpClient("localhost", port, "student", "bad")) {
		} catch (IOException e) {
			denied = true;
		}
		check(denied, "FTP verifies username/password");
		check(
				FtpClient.register("localhost", port, "tester", "123").startsWith("200"),
				"FTP account registration through SITE extension");
		try (FtpClient c = new FtpClient("localhost", port, "tester", "123")) {
			check(
					Arrays.equals(blob, c.download("binary.bin")),
					"FTP registered user can log in and access shared files");
		}
		Path downloaded = dir.resolve("curl-ftp.bin");
		Process curl =
				new ProcessBuilder(
								"curl.exe",
								"--silent",
								"--show-error",
								"--max-time",
								"10",
								"--user",
								"student:student123",
								"ftp://127.0.0.1:" + port + "/binary.bin",
								"--output",
								downloaded.toString())
						.redirectErrorStream(true)
						.redirectOutput(dir.resolve("curl.log").toFile())
						.start();
		check(
				curl.waitFor() == 0 && Arrays.equals(blob, Files.readAllBytes(downloaded)),
				"FTP interoperability with independent curl client");
		stop(server);
		server = start("server.FtpServer", "" + port, dir.resolve("ftp").toString());
		ready(port);
		try (FtpClient c = new FtpClient("localhost", port, "student", "student123")) {
			check(
					Arrays.equals(blob, c.download("binary.bin")),
					"FTP files/accounts survive restart");
		}
		Db database = new Db(mysqlConfig);
		check(database.auth("tester", "123"), "Registered FTP account is stored in MySQL");
		check(
				((Number)
										database.rows(
														"SELECT size FROM files WHERE"
																+ " path='binary.bin'")
												.getFirst()[0])
								.longValue()
						== blob.length,
				"Upload metadata is stored in MySQL");
		check(
				!database.rows("SELECT remote_ip FROM sessions WHERE username='student'").isEmpty(),
				"FTP session IP and login records exist in MySQL");
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
			ftp();
			lifecycle();
			System.out.println("ALL " + checks + " CHECKS PASSED");
		} finally {
			for (Process p : processes) if (p.isAlive()) stop(p);
			TestDatabase.cleanup();
		}
	}
}
