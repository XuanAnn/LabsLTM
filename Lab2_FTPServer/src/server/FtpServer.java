package server;

import client.*;
import server.*;
import common.*;

import common.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** FTP control and data channels implemented directly on TCP sockets. */
public final class FtpServer implements AutoCloseable {
	private final Db db;
	private final Path root;
	private volatile ServerSocket listener;
	private final Set<Socket> clients = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public FtpServer(Path root) throws Exception {
		this.root = root.toAbsolutePath().normalize();
		Files.createDirectories(this.root);
		db = new Db();
		db.user("student", "student123");
		db.user("alice", "123");
		db.user("bob", "123");
	}

	public void serve(int port) throws Exception {
		try (ServerSocket server = new ServerSocket(port)) {
			listener = server;
			System.out.println("FTP :" + server.getLocalPort() + " root=" + root);
			while (!server.isClosed()) {
				Socket socket = server.accept();
				clients.add(socket);
				Thread.startVirtualThread(() -> session(socket));
			}
		}
	}

	private void session(Socket socket) {
		ServerSocket passive = null;
		String sessionId = null;
		try (socket) {
			socket.setSoTimeout(120000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			Wire.line(out, "220 LTM FTP ready");
			String user = "";
			boolean login = false;
			Path home = null, cwd = null;
			for (String line; (line = Wire.line(in)) != null; ) {
				String[] parts = line.split(" ", 2);
				String cmd = parts[0].toUpperCase(Locale.ROOT),
						arg = parts.length == 2 ? parts[1] : "";
				if (cmd.equals("QUIT")) {
					Wire.line(out, "221 Bye");
					break;
				}
				if (cmd.equals("USER")) {
					user = arg;
					login = false;
					Wire.line(out, "331 Password required");
					continue;
				}
				if (cmd.equals("PASS")) {
					login = db.auth(user, arg);
					if (login) {
						home = root.resolve("files");
						Files.createDirectories(home);
						cwd = home;
						db.sessionEnd(sessionId);
						sessionId =
								db.sessionStart(
										user, socket.getRemoteSocketAddress().toString(), "FTP");
					}
					Wire.line(
							out,
							login ? "230 Logged in as " + user : "530 Incorrect username/password");
					continue;
				}
				if (cmd.equals("SYST")) {
					Wire.line(out, "215 UNIX Type: L8");
					continue;
				}
				if (cmd.equals("FEAT")) {
					Wire.line(out, "211-Features");
					Wire.line(out, " EPSV");
					Wire.line(out, " UTF8");
					Wire.line(out, " SIZE");
					Wire.line(out, "211 End");
					continue;
				}
				if (cmd.equals("SITE") && arg.startsWith("REGISTER ")) {
					try {
						String[] parts2 = arg.substring(9).split(" ", 2);
						if (parts2.length != 2 || !parts2[0].matches("[A-Za-z0-9_-]{1,24}")) {
							throw new IllegalArgumentException();
						}
						String pass =
								new String(
										Base64.getDecoder().decode(parts2[1]),
										StandardCharsets.UTF_8);
						if (pass.isEmpty() || pass.length() > 128) {
							throw new IllegalArgumentException();
						}
						Wire.line(
								out,
								db.register(parts2[0], pass)
										? "200 Account registered"
										: "550 Username already exists");
					} catch (IllegalArgumentException exception) {
						Wire.line(out, "501 SITE REGISTER username base64-password");
					}
					continue;
				}
				if (!login) {
					Wire.line(out, "530 Please login");
					continue;
				}
				try {
					switch (cmd) {
						case "NOOP", "OPTS" -> Wire.line(out, "200 OK");
						case "TYPE" ->
								Wire.line(
										out,
										arg.equalsIgnoreCase("I") || arg.equalsIgnoreCase("A")
												? "200 Type set"
												: "504 Unsupported type");
						case "PWD" ->
								Wire.line(
										out,
										"257 \"/"
												+ home.relativize(cwd).toString().replace('\\', '/')
												+ "\"");
						case "CWD", "CDUP" -> {
							Path filePath = resolve(home, cwd, cmd.equals("CDUP") ? ".." : arg);
							if (!Files.isDirectory(filePath)) {
								throw new IOException("Not a directory");
							}
							cwd = filePath;
							Wire.line(out, "250 Directory changed");
						}
						case "MKD" -> {
							Files.createDirectory(resolve(home, cwd, arg));
							Wire.line(out, "257 Directory created");
						}
						case "DELE" -> {
							Path filePath2 = resolve(home, cwd, arg);
							if (!Files.isRegularFile(filePath2)) {
								throw new IOException("Not a file");
							}
							Files.delete(filePath2);
							db.exec(
									"DELETE FROM files WHERE path=?",
									home.relativize(filePath2).toString().replace('\\', '/'));
							db.audit(user + " DELETE " + arg);
							Wire.line(out, "250 Deleted");
						}
						case "RMD" -> {
							Path filePath3 = resolve(home, cwd, arg);
							if (filePath3.equals(home)) {
								throw new IOException("Root protected");
							}
							Files.delete(filePath3);
							Wire.line(out, "250 Removed");
						}
						case "SIZE" -> Wire.line(out, "213 " + Files.size(resolve(home, cwd, arg)));
						case "PASV", "EPSV" -> {
							if (passive != null) {
								passive.close();
							}
							passive = new ServerSocket(0, 1, socket.getLocalAddress());
							passive.setSoTimeout(15000);
							int count = passive.getLocalPort();
							if (cmd.equals("EPSV")) {
								Wire.line(
										out,
										"229 Entering Extended Passive Mode (|||" + count + "|)");
							} else {
								String address =
										System.getenv()
												.getOrDefault(
														"FTP_PUBLIC_HOST",
														socket.getLocalAddress().getHostAddress());
								Wire.line(
										out,
										"227 Entering Passive Mode ("
												+ address.replace('.', ',')
												+ ","
												+ count / 256
												+ ","
												+ count % 256
												+ ")");
							}
						}
						case "LIST", "NLST", "RETR", "STOR" -> {
							if (passive == null) {
								Wire.line(out, "425 Use PASV/EPSV first");
								break;
							}
							Path filePath4 =
									resolve(
											home,
											cwd,
											arg.isBlank() || arg.startsWith("-") ? "." : arg);
							if (cmd.equals("RETR") && !Files.isRegularFile(filePath4)) {
								throw new IOException("File unavailable");
							}
							if (cmd.equals("STOR")
									&& (filePath4.equals(home) || Files.isDirectory(filePath4))) {
								throw new IOException("Invalid file");
							}
							Wire.line(out, "150 Opening data connection");
							try (ServerSocket listener = passive;
									Socket data = listener.accept()) {
								passive = null;
								data.setSoTimeout(15000);
								if (!data.getInetAddress().equals(socket.getInetAddress())) {
									throw new IOException("Data peer mismatch");
								}
								if (cmd.equals("RETR")) {
									try (InputStream stream = Files.newInputStream(filePath4)) {
										stream.transferTo(data.getOutputStream());
									}
								} else if (cmd.equals("STOR")) {
									Path temp =
											Files.createTempFile(
													filePath4.getParent(), "upload-", ".tmp");
									try {
										try (OutputStream stream2 = Files.newOutputStream(temp)) {
											data.getInputStream().transferTo(stream2);
										}
										Files.move(
												temp,
												filePath4,
												StandardCopyOption.REPLACE_EXISTING);
										if (SaveOptions.enabled(SaveOptions.Kind.FILE_METADATA)) {
											db.exec(
													"INSERT INTO files(path,owner,size)"
														+ " VALUES(?,?,?) ON DUPLICATE KEY UPDATE"
														+ " owner=VALUES(owner),size=VALUES(size)",
													home.relativize(filePath4)
															.toString()
															.replace('\\', '/'),
													user,
													Files.size(filePath4));
										}
									} finally {
										Files.deleteIfExists(temp);
									}
								} else {
									try (Stream<Path> entries = Files.list(filePath4)) {
										for (Path filePath5 : entries.sorted().toList()) {
											String text =
													cmd.equals("NLST")
															? filePath5.getFileName().toString()
															: (Files.isDirectory(filePath5)
																			? "drwxr-xr-x"
																			: "-rw-r--r--")
																	+ " 1 owner group "
																	+ (Files.isDirectory(filePath5)
																			? 0
																			: Files.size(filePath5))
																	+ " Jan 01 00:00 "
																	+ filePath5.getFileName();
											data.getOutputStream()
													.write(
															(text + "\r\n")
																	.getBytes(
																			StandardCharsets
																					.UTF_8));
										}
									}
								}
							}
							db.audit(user + " " + cmd + " " + arg);
							Wire.line(out, "226 Transfer complete");
						}
						default -> Wire.line(out, "502 Command not implemented");
					}
				} catch (Exception exception2) {
					if (passive != null) {
						passive.close();
						passive = null;
					}
					Wire.line(
							out,
							"550 " + exception2.getClass().getSimpleName() + ": operation failed");
				}
			}
		} catch (Exception exception3) {
			System.err.println("FTP session: " + exception3.getMessage());
		} finally {
			clients.remove(socket);
			try {
				db.sessionEnd(sessionId);
			} catch (Exception exception4) {
				System.err.println(exception4.getMessage());
			}
			if (passive != null) {
				try {
					passive.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private Path resolve(Path home, Path cwd, String text) throws IOException {
		String rel = text.replace('\\', '/');
		Path filePath = rel.startsWith("/") ? home.resolve(rel.substring(1)) : cwd.resolve(rel);
		return Wire.inside(home, home.relativize(filePath.normalize()).toString());
	}

	public static void main(String[] args) throws Exception {
		new FtpServer(args.length > 1 ? Path.of(args[1]) : LabPaths.resolve(Path.of("data/ftp")))
				.serve(Integer.parseInt(Wire.arg(args, 0, "2121")));
	}

	public void close() throws IOException {
		if (listener != null) {
			listener.close();
		}
		for (Socket socket : clients) {
			socket.close();
		}
	}
}
