import common.*;

import server.ServerController;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class IntegrationTest {
	static int count;

	static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
		count++;
		System.out.println("PASS " + message);
	}

	record Peer(Socket socket, ObjectInputStream in, ObjectOutputStream out)
			implements AutoCloseable {
		void send(Message m) throws Exception {
			out.writeObject(m);
			out.flush();
			out.reset();
		}

		Message await(Message.Type type) throws Exception {
			for (int i = 0; i < 100; i++) {
				Message m = (Message) in.readObject();
				if (m.getType() == type) return m;
			}
			throw new AssertionError(type);
		}

		public void close() throws IOException {
			socket.close();
		}
	}

	static Peer connect(int port) throws Exception {
		Socket s = new Socket("127.0.0.1", port);
		s.setSoTimeout(5000);
		ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
		out.flush();
		return new Peer(s, new ObjectInputStream(s.getInputStream()), out);
	}

	static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
		long end = System.currentTimeMillis() + 6000;
		while (System.currentTimeMillis() < end) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(30);
		}
		throw new AssertionError("Timed out");
	}

	static class Events implements client.ClientController.ClientEventListener {
		volatile boolean login;
		volatile int groups;

		public void onLoginResult(boolean success, String message) {
			login = success;
		}

		public void onRegisterResult(boolean success, String message) {}

		public void onUserListUpdated(List<UserStatus> users) {}

		public void onGroupListUpdated(List<GroupInfo> groups) {}

		public void onDirectMessageReceived(String sender, String text, long at) {}

		public void onGroupMessageReceived(String id, String sender, String text, long at) {
			if (text.equals("multicast-test")) groups++;
		}

		public void onFileReceived(String sender, String name, byte[] data, long at) {}

		public void onSystemNotification(String value) {}

		public void onConnectionClosed(String reason) {}
	}

	public static void main(String[] args) throws Exception {
		Files.createDirectories(Path.of("target"));
		Path dir = Files.createTempDirectory(Path.of("target"), "lab1-test-");
		System.setProperty("lab1.data.dir", dir.toAbsolutePath().toString());
		ChatDatabaseFixture databaseFixture = new ChatDatabaseFixture(dir);
		ServerController server = new ServerController(null);
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		server.startServer(port);
		waitFor(server::isRunning);
		List<Peer> peers = new ArrayList<>();
		try {
			for (int i = 0; i < 6; i++) {
				Peer p = connect(port);
				peers.add(p);
				String user = "test" + i;
				p.send(new Message(Message.Type.REGISTER, user, "SERVER", "123"));
				check(p.await(Message.Type.REGISTER_SUCCESS) != null, "Register user " + user);
				p.send(new Message(Message.Type.LOGIN, user, "SERVER", "123"));
				p.await(Message.Type.LOGIN_SUCCESS);
			}
			waitFor(() -> server.getOnlineUsers().size() == 6);
			check(true, "Six authenticated clients online with MySQL chat history");
			peers.get(0).send(new Message(Message.Type.CHAT_DIRECT, "fake", "test1", "hello"));
			check(
					peers.get(1).await(Message.Type.CHAT_DIRECT).getSender().equals("test0"),
					"Unicast uses logged-in sender identity");
			server.ChatDatabase database = new server.ChatDatabase();
			check(database.loadHistory().size() == 1
					&& database.loadHistory().getFirst().getSender().equals("test0")
					&& database.loadHistory().getFirst().getMessage().equals("hello"),
					"Direct message stored in MySQL with authenticated sender");
			peers.get(1).send(new Message(Message.Type.CHAT_GROUP, "fake", "group-test", "Nội dung nhóm\nDòng hai"));
			waitFor(() -> server.getChatHistory().size() == 2);
			check(database.loadHistory().get(1).getTargetType() == ChatRecord.TargetType.GROUP
					&& database.loadHistory().get(1).getMessage().equals("Nội dung nhóm\nDòng hai"),
					"Group history stores Unicode and multiline content in MySQL");
			ServerController persisted = new ServerController(null);
			check(persisted.getChatHistory().size() == 2
					&& persisted.getChatHistory().getFirst().getTimestamp() == database.loadHistory().getFirst().getTimestamp(),
					"Fresh server controller restores ordered chat history and timestamps");
			Message file = new Message(Message.Type.FILE_TRANSFER, "test0", "ALL", "");
			file.setFileName("empty.txt");
			file.setFileData(new byte[0]);
			peers.get(0).send(file);
			for (Peer p : peers)
				check(
						p.await(Message.Type.FILE_TRANSFER).getFileData().length == 0,
						"TCP fan-out file broadcast handles empty file");
			try (Peer replacement = connect(port)) {
				replacement.send(new Message(Message.Type.LOGIN, "test0", "SERVER", "123"));
				replacement.await(Message.Type.LOGIN_SUCCESS);
				waitFor(() -> server.isUserOnline("test0"));
				check(
						server.isUserOnline("test0"),
						"Replacing a login does not remove the new session");
			}
			var first = server.createNewGroup("Before restart", "test1");
			ServerController reload = new ServerController(null);
			var second = reload.createNewGroup("After restart", "test1");
			check(
					!first.getMulticastAddress().equals(second.getMulticastAddress())
							&& first.getMulticastPort() != second.getMulticastPort(),
					"Saved multicast addresses and ports are not reused after reload");
			check(reload.authenticate("test1", "123"), "Accounts persist in text files");
			server.registerAccount("multicast1", "123");
			server.registerAccount("multicast2", "123");
			Events e1 = new Events(), e2 = new Events();
			client.ClientController one = new client.ClientController(e1),
					two = new client.ClientController(e2);
			try {
				one.connect("127.0.0.1", port);
				two.connect("127.0.0.1", port);
				one.login("multicast1", "123");
				two.login("multicast2", "123");
				waitFor(() -> e1.login && e2.login);
				GroupInfo group = server.createNewGroup("UDP test", "multicast1");
				one.requestJoinGroup(group);
				two.requestJoinGroup(group);
				for (int attempt = 0;
						attempt < 30 && (e1.groups == 0 || e2.groups == 0);
						attempt++) {
					Thread.sleep(100);
					one.sendGroupMessage(group, "multicast-test");
				}
				waitFor(() -> e1.groups > 0 && e2.groups > 0);
				check(
						true,
						"Group chat UDP multicast reaches two real ClientControllers on the same"
								+ " interface");
				waitFor(() -> {
					for (ChatRecord record : server.getChatHistory()) {
						if (record.getTargetType() == ChatRecord.TargetType.GROUP && record.getMessage().equals("multicast-test")) return true;
					}
					return false;
				});
				boolean storedMulticast = false;
				for (ChatRecord record : database.loadHistory()) {
					if (record.getTargetType() == ChatRecord.TargetType.GROUP && record.getMessage().equals("multicast-test")) storedMulticast = true;
				}
				check(storedMulticast, "Actual UDP group send also archives its history in MySQL");
			} finally {
				one.disconnect("test finished");
				two.disconnect("test finished");
			}
		} finally {
			for (Peer p : peers)
				try {
					p.close();
				} catch (Exception ignored) {
				}
			server.stopServer();
			databaseFixture.close();
		}
		System.out.println("ALL " + count + " CHECKS PASSED");
	}
}
