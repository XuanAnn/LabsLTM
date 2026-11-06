package server;

import common.ChatRecord;
import common.GroupInfo;
import common.Message;
import common.UserStatus;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ServerController {
	public interface ServerEventListener {
		void onServerStarted(int port);

		void onServerStopped();

		void onUserStatusChanged(String username, boolean online);

		void onUserListUpdated(
				Map<String, UserAccount> accounts, Map<String, String> onlineUserIps);

		void onGroupListUpdated(Map<String, GroupInfo> groups);

		void onMessageLogged(ChatRecord record);

		void onSystemLog(String log);
	}

	private int port = 8888;
	private ServerSocket serverSocket;
	private volatile boolean isRunning = false;
	private ServerEventListener listener;

	// Account store: username -> UserAccount (username, password, createdAt)
	private final Map<String, UserAccount> userAccounts = new ConcurrentHashMap<>();
	// Client connections: username -> ClientHandler
	private final Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
	// Groups: groupId -> GroupInfo
	private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();
	// Message records
	private ChatDatabase chatDatabase;
	private final List<ChatRecord> chatHistory = new CopyOnWriteArrayList<>();

	// Thread pool for handlers
	private ExecutorService threadPool = Executors.newCachedThreadPool();

	// Multicast pool configuration
	private int nextMulticastThirdOctet = 0;
	private int nextMulticastFourthOctet = 1;
	private final int multicastBasePort = 50000;
	private int nextMulticastPort = multicastBasePort;

	// Thư mục gốc lưu trữ user accounts: src/server/accounts/
	private File accountsBaseDir;
	// Thư mục gốc lưu trữ groups: src/server/groups/
	private File groupsBaseDir;

	public ServerController(ServerEventListener listener) {
		this.listener = listener;
		String customData = System.getProperty("lab1.data.dir");
		if (customData == null) {
			initAccountsDirectory();
			initGroupsDirectory();
		} else {
			accountsBaseDir = new File(customData, "accounts");
			groupsBaseDir = new File(customData, "groups");
			accountsBaseDir.mkdirs();
			groupsBaseDir.mkdirs();
		}
		loadAccounts();
		loadGroups();
		try {
			chatDatabase = new ChatDatabase();
			chatHistory.addAll(chatDatabase.loadHistory());
		} catch (Exception exception) {
			throw new IllegalStateException("Không kết nối được MySQL lưu chat. Kiểm tra config/database.properties", exception);
		}
	}

	private void initAccountsDirectory() {
		// Ưu tiên lưu trực tiếp vào: src/server/accounts
		File srcDir = new File("src" + File.separator + "server" + File.separator + "accounts");
		if (srcDir.getParentFile() != null && srcDir.getParentFile().exists()) {
			this.accountsBaseDir = srcDir;
		} else {
			// Hoặc trong bin/server/accounts
			File binDir = new File("bin" + File.separator + "server" + File.separator + "accounts");
			if (binDir.getParentFile() != null && binDir.getParentFile().exists()) {
				this.accountsBaseDir = binDir;
			} else {
				this.accountsBaseDir =
						new File("src" + File.separator + "server" + File.separator + "accounts");
			}
		}
		if (!this.accountsBaseDir.exists()) {
			this.accountsBaseDir.mkdirs();
		}
	}

	private void initGroupsDirectory() {
		// Ưu tiên lưu trực tiếp vào: src/server/groups
		File srcDir = new File("src" + File.separator + "server" + File.separator + "groups");
		if (srcDir.getParentFile() != null && srcDir.getParentFile().exists()) {
			this.groupsBaseDir = srcDir;
		} else {
			File binDir = new File("bin" + File.separator + "server" + File.separator + "groups");
			if (binDir.getParentFile() != null && binDir.getParentFile().exists()) {
				this.groupsBaseDir = binDir;
			} else {
				this.groupsBaseDir =
						new File("src" + File.separator + "server" + File.separator + "groups");
			}
		}
		if (!this.groupsBaseDir.exists()) {
			this.groupsBaseDir.mkdirs();
		}
	}

	private void loadGroups() {
		if (!groupsBaseDir.exists()) {
			groupsBaseDir.mkdirs();
		}

		File[] groupDirs = groupsBaseDir.listFiles(File::isDirectory);
		if (groupDirs == null || groupDirs.length == 0) {
			// Nhóm mặc định nếu chưa có nhóm nào được lưu
			createNewGroup("General", "system");
			return;
		}

		for (File dir : groupDirs) {
			String dirName = dir.getName();
			File infoFile = new File(dir, "group.txt");
			if (infoFile.exists()) {
				GroupInfo g = readGroupFile(infoFile, dirName);
				if (g != null) {
					groups.put(g.getGroupId(), g);
					// Cập nhật lại multicast pool port để tránh trùng
					if (g.getMulticastPort() >= nextMulticastPort) {
						nextMulticastPort = g.getMulticastPort() + 1;
					}
					// Continue after the largest saved address instead of reusing 230.0.0.1 after
					// restart.
					String[] octets = g.getMulticastAddress().split("\\.");
					if (octets.length == 4) {
						int third = Integer.parseInt(octets[2]),
								fourth = Integer.parseInt(octets[3]);
						if (third > nextMulticastThirdOctet
								|| (third == nextMulticastThirdOctet
										&& fourth >= nextMulticastFourthOctet)) {
							nextMulticastThirdOctet = third;
							nextMulticastFourthOctet = fourth + 1;
							if (nextMulticastFourthOctet > 250) {
								nextMulticastFourthOctet = 1;
								nextMulticastThirdOctet++;
							}
						}
					}
				}
			}
		}

		if (groups.isEmpty()) {
			createNewGroup("General", "system");
		}
	}

	private GroupInfo readGroupFile(File file, String fallbackGroupId) {
		String groupId = fallbackGroupId;
		String groupName = fallbackGroupId;
		String multicastAddress = "230.0.0.1";
		int multicastPort = 50000;
		Set<String> members = new HashSet<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				int colonIdx = line.indexOf(':');
				if (colonIdx > 0) {
					String key = line.substring(0, colonIdx).trim();
					String val = line.substring(colonIdx + 1).trim();

					if ("groupId".equalsIgnoreCase(key) || "id".equalsIgnoreCase(key)) {
						groupId = val;
					} else if ("groupName".equalsIgnoreCase(key) || "name".equalsIgnoreCase(key)) {
						groupName = val;
					} else if ("multicastAddress".equalsIgnoreCase(key)
							|| "multicastIp".equalsIgnoreCase(key)
							|| "ip".equalsIgnoreCase(key)) {
						multicastAddress = val;
					} else if ("multicastPort".equalsIgnoreCase(key)
							|| "port".equalsIgnoreCase(key)) {
						try {
							multicastPort = Integer.parseInt(val);
						} catch (NumberFormatException ignored) {
						}
					} else if ("members".equalsIgnoreCase(key)) {
						if (!val.isEmpty()) {
							String[] mems = val.split(",");
							for (String text : mems) {
								String clean = text.trim();
								if (!clean.isEmpty()) {
									members.add(clean);
								}
							}
						}
					}
				}
			}
			GroupInfo g = new GroupInfo(groupId, groupName, multicastAddress, multicastPort);
			g.setMembers(members);
			return g;
		} catch (IOException exception) {
			System.err.println(
					"Lỗi đọc file group.txt: " + file.getPath() + " -> " + exception.getMessage());
			return null;
		}
	}

	private synchronized int getNextGroupIndex() {
		int maxIndex = 0;
		if (groupsBaseDir.exists()) {
			File[] dirs = groupsBaseDir.listFiles(File::isDirectory);
			if (dirs != null) {
				for (File d : dirs) {
					String name = d.getName();
					if (name.startsWith("group_")) {
						try {
							String[] parts = name.split("_");
							if (parts.length >= 2) {
								int idx = Integer.parseInt(parts[1]);
								if (idx > maxIndex) {
									maxIndex = idx;
								}
							}
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
		}
		return maxIndex + 1;
	}

	private String sanitizeFolderName(String name) {
		if (name == null) {
			return "group";
		}
		String clean = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
		return clean.isEmpty() ? "group" : clean;
	}

	private File findGroupDirectory(String groupId) {
		if (groupsBaseDir.exists()) {
			File[] dirs = groupsBaseDir.listFiles(File::isDirectory);
			if (dirs != null) {
				for (File d : dirs) {
					File infoFile = new File(d, "group.txt");
					if (infoFile.exists()) {
						try (BufferedReader reader = new BufferedReader(new FileReader(infoFile))) {
							String line;
							while ((line = reader.readLine()) != null) {
								line = line.trim();
								if (line.startsWith("groupId:") || line.startsWith("id:")) {
									String idVal = line.substring(line.indexOf(':') + 1).trim();
									if (groupId.equals(idVal)) {
										return d;
									}
								}
							}
						} catch (IOException ignored) {
						}
					}
				}
			}
		}
		return null;
	}

	public synchronized void saveGroupFile(GroupInfo group) {
		File groupDir = findGroupDirectory(group.getGroupId());
		if (groupDir == null) {
			int seq = getNextGroupIndex();
			String folderName =
					String.format("group_%03d_%s", seq, sanitizeFolderName(group.getGroupName()));
			groupDir = new File(groupsBaseDir, folderName);
			groupDir.mkdirs();
		}
		File infoFile = new File(groupDir, "group.txt");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(infoFile))) {
			writer.write("groupId: " + group.getGroupId());
			writer.newLine();
			writer.write("groupName: " + group.getGroupName());
			writer.newLine();
			writer.write("multicastAddress: " + group.getMulticastAddress());
			writer.newLine();
			writer.write("multicastPort: " + group.getMulticastPort());
			writer.newLine();
			writer.write("members: " + String.join(", ", group.getMembers()));
			writer.newLine();
		} catch (IOException exception) {
			System.err.println("Lỗi lưu group.txt: " + exception.getMessage());
		}
	}

	public synchronized GroupInfo createNewGroup(String groupName, String creator) {
		int seq = getNextGroupIndex();
		String groupId = String.format("group_%03d", seq);
		String mcastIp =
				String.format("230.0.%d.%d", nextMulticastThirdOctet, nextMulticastFourthOctet);
		nextMulticastFourthOctet++;
		if (nextMulticastFourthOctet > 250) {
			nextMulticastFourthOctet = 1;
			nextMulticastThirdOctet++;
		}
		int port = nextMulticastPort++;
		GroupInfo group = new GroupInfo(groupId, groupName, mcastIp, port);
		if (creator != null && !creator.equals("system")) {
			group.addMember(creator);
		}
		groups.put(groupId, group);
		saveGroupFile(group);
		notifyGroupUpdate();
		log("Tạo nhóm mới: " + groupName + " (" + groupId + ") -> " + mcastIp + ":" + port);
		return group;
	}

	public synchronized void startServer(int port) {
		if (isRunning) {
			return;
		}
		if (threadPool.isShutdown()) {
			threadPool = Executors.newCachedThreadPool();
		}
		this.port = port;
		new Thread(
						() -> {
							try {
								serverSocket = new ServerSocket(port);
								isRunning = true;
								if (listener != null) {
									listener.onServerStarted(port);
								}
								log("Server đã khởi động trên cổng: " + port);

								while (isRunning) {
									try {
										Socket clientSocket = serverSocket.accept();
										ClientHandler handler =
												new ClientHandler(clientSocket, this);
										threadPool.execute(handler);
									} catch (SocketException exception) {
										if (!isRunning) {
											break;
										}
									}
								}
							} catch (IOException exception2) {
								log("Lỗi khởi động Server: " + exception2.getMessage());
							} finally {
								stopServer();
							}
						})
				.start();
	}

	public synchronized void stopServer() {
		if (!isRunning) {
			return;
		}
		isRunning = false;
		try {
			Message shutMsg =
					new Message(
							Message.Type.SYSTEM_NOTIFICATION,
							"SERVER",
							"ALL",
							"Server đang dừng hoạt động.");
			broadcastToAll(shutMsg);

			for (ClientHandler client : onlineClients.values()) {
				client.closeConnection();
			}
			onlineClients.clear();
			threadPool.shutdownNow();

			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
			if (listener != null) {
				listener.onServerStopped();
			}
			log("Server đã dừng.");
			notifyUserUpdate();
		} catch (IOException exception) {
			log("Lỗi khi dừng server: " + exception.getMessage());
		}
	}

	public boolean isRunning() {
		return isRunning;
	}

	// Quản lý Account theo cấu trúc: accountsBaseDir/<username>/account.txt
	private void loadAccounts() {
		if (!accountsBaseDir.exists()) {
			accountsBaseDir.mkdirs();
		}

		File[] userDirs = accountsBaseDir.listFiles(File::isDirectory);

		for (File dir : userDirs) {
			String username = dir.getName();
			File accFile = new File(dir, "account.txt");
			if (accFile.exists()) {
				UserAccount account = readAccountFile(accFile, username);
				if (account != null) {
					userAccounts.put(account.getUsername(), account);
				}
			}
		}
	}

	private UserAccount readAccountFile(File file, String fallbackUsername) {
		String username = fallbackUsername;
		String password = "";
		String createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String lastLoginTime = "-";
		String lastLogoutTime = "-";

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				int colonIdx = line.indexOf(':');
				if (colonIdx > 0) {
					String key = line.substring(0, colonIdx).trim();
					String val = line.substring(colonIdx + 1).trim();

					if ("username".equalsIgnoreCase(key)) {
						username = val;
					} else if ("password".equalsIgnoreCase(key)) {
						password = val;
					} else if ("createdAt".equalsIgnoreCase(key)
							|| "created_at".equalsIgnoreCase(key)) {
						createdAt = val;
					} else if ("lastLoginTime".equalsIgnoreCase(key)
							|| "last_login".equalsIgnoreCase(key)
							|| "loginTime".equalsIgnoreCase(key)) {
						lastLoginTime = val;
					} else if ("lastLogoutTime".equalsIgnoreCase(key)
							|| "last_logout".equalsIgnoreCase(key)
							|| "logoutTime".equalsIgnoreCase(key)) {
						lastLogoutTime = val;
					}
				}
			}
			return new UserAccount(username, password, createdAt, lastLoginTime, lastLogoutTime);
		} catch (IOException exception) {
			System.err.println(
					"Lỗi đọc file account: " + file.getPath() + " -> " + exception.getMessage());
			return null;
		}
	}

	private synchronized void saveAccountFile(UserAccount acc) {
		File userDir = new File(accountsBaseDir, acc.getUsername());
		if (!userDir.exists()) {
			userDir.mkdirs();
		}
		File accFile = new File(userDir, "account.txt");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(accFile))) {
			writer.write("username: " + acc.getUsername());
			writer.newLine();
			writer.write("password: " + acc.getPassword());
			writer.newLine();
			writer.write("createdAt: " + acc.getCreatedAt());
			writer.newLine();
			writer.write("lastLoginTime: " + acc.getLastLoginTime());
			writer.newLine();
			writer.write("lastLogoutTime: " + acc.getLastLogoutTime());
			writer.newLine();
		} catch (IOException exception) {
			System.err.println("Lỗi lưu account.txt: " + exception.getMessage());
		}
	}

	private synchronized void createAccountFolder(
			String username, String password, String createdAt) {
		UserAccount acc = new UserAccount(username, password, createdAt);
		saveAccountFile(acc);
		userAccounts.put(username, acc);
	}

	public synchronized boolean registerAccount(String username, String password) {
		if (userAccounts.containsKey(username)) {
			return false;
		}
		String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		createAccountFolder(username, password, now);
		notifyUserUpdate();
		broadcastUserList();
		log("Người dùng mới đã đăng ký: " + username);
		return true;
	}

	public synchronized boolean deleteAccount(String username) {
		if (!userAccounts.containsKey(username)) {
			return false;
		}
		kickUser(username);
		userAccounts.remove(username);

		// Xoá thư mục accounts/<username>/
		File userDir = new File(accountsBaseDir, username);
		if (userDir.exists()) {
			File[] files = userDir.listFiles();
			if (files != null) {
				for (File file : files) {
					file.delete();
				}
			}
			userDir.delete();
		}

		// Xoá khỏi các nhóm chat
		for (GroupInfo g : groups.values()) {
			if (g.hasMember(username)) {
				g.removeMember(username);
				saveGroupFile(g);
			}
		}

		notifyUserUpdate();
		notifyGroupUpdate();
		broadcastUserList();
		broadcastGroupList();
		log("Đã xoá tài khoản: " + username);
		return true;
	}

	public synchronized boolean resetPassword(String username, String newPassword) {
		UserAccount acc = userAccounts.get(username);
		if (acc == null) {
			return false;
		}
		acc.setPassword(newPassword);

		// Cập nhật lại file account.txt
		createAccountFolder(username, newPassword, acc.getCreatedAt());
		log("Đã cập nhật mật khẩu cho: " + username);
		return true;
	}

	public synchronized void kickUser(String username) {
		ClientHandler handler = onlineClients.remove(username);
		if (handler != null) {
			Message kickMsg =
					new Message(
							Message.Type.SYSTEM_NOTIFICATION,
							"SERVER",
							username,
							"Bạn đã bị ngắt kết nối bởi Quản trị viên.");
			handler.sendMessage(kickMsg);
			handler.closeConnection();
			log("Đã kick tài khoản: " + username);
			notifyUserUpdate();
			broadcastUserList();
		}
	}

	public boolean authenticate(String username, String password) {
		UserAccount acc = userAccounts.get(username);
		return acc != null && acc.getPassword().equals(password);
	}

	public void addOnlineUser(String username, ClientHandler handler) {
		ClientHandler old = onlineClients.put(username, handler);
		if (old != null && old != handler) {
			old.sendMessage(
					new Message(
							Message.Type.SYSTEM_NOTIFICATION,
							"SERVER",
							username,
							"Đã đăng nhập từ một phiên kết nối khác."));
			old.closeConnection();
		}

		// Cập nhật thời gian vào (lastLoginTime)
		String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		UserAccount acc = userAccounts.get(username);
		if (acc != null) {
			acc.setLastLoginTime(now);
			saveAccountFile(acc);
		}

		log("Người dùng Online: " + username + " (" + handler.getClientAddress() + ")");
		notifyUserUpdate();
		broadcastUserList();
	}

	public synchronized void removeOnlineUser(String username) {
		removeOnlineUser(username, null);
	}

	public synchronized void removeOnlineUser(String username, ClientHandler expected) {
		if (expected != null && onlineClients.get(username) != expected) {
			return;
		}
		if (onlineClients.remove(username) != null) {
			// Cập nhật thời gian ra (lastLogoutTime)
			String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
			UserAccount acc = userAccounts.get(username);
			if (acc != null) {
				acc.setLastLogoutTime(now);
				saveAccountFile(acc);
			}

			log("Người dùng Offline: " + username);
			notifyUserUpdate();
			broadcastUserList();
		}
	}

	public boolean isUserOnline(String username) {
		return onlineClients.containsKey(username);
	}

	// Direct Messaging
	public void handleDirectMessage(Message msg) {
		recordChat(
				ChatRecord.TargetType.DIRECT, msg.getSender(), msg.getReceiver(), msg.getContent());

		ClientHandler receiverHandler = onlineClients.get(msg.getReceiver());
		if (receiverHandler != null) {
			receiverHandler.sendMessage(msg);
		} else {
			ClientHandler senderHandler = onlineClients.get(msg.getSender());
			if (senderHandler != null) {
				Message offMsg =
						new Message(
								Message.Type.SYSTEM_NOTIFICATION,
								"SERVER",
								msg.getSender(),
								"Người dùng '"
										+ msg.getReceiver()
										+ "' hiện đang Offline. Tin nhắn chưa thể chuyển trực"
										+ " tiếp.");
				senderHandler.sendMessage(offMsg);
			}
		}
	}

	// Broadcast file to all connected online clients
	public void handleFileBroadcast(Message msg) {
		String fileName = msg.getFileName();
		long size = msg.getFileData() != null ? msg.getFileData().length : 0;
		String sizeFormatted = formatFileSize(size);

		recordChat(
				ChatRecord.TargetType.DIRECT,
				msg.getSender(),
				"ALL (Broadcast File)",
				"📁 Đã chia sẻ file: " + fileName + " (" + sizeFormatted + ")");
		log(
				"Người dùng "
						+ msg.getSender()
						+ " đã phát sóng (Broadcast) file: "
						+ fileName
						+ " ("
						+ sizeFormatted
						+ ")");

		// Broadcast to all connected clients
		broadcastToAll(msg);
	}

	private String formatFileSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(1024));
		char pre = "KMGTPE".charAt(exp - 1);
		return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
	}

	// Group Management
	public synchronized void joinGroup(String groupId, String username) {
		GroupInfo g = groups.get(groupId);
		if (g != null) {
			g.addMember(username);
			saveGroupFile(g);
			notifyGroupUpdate();
			broadcastGroupList();
			log("Người dùng " + username + " đã tham gia nhóm " + g.getGroupName());
		}
	}

	public synchronized void leaveGroup(String groupId, String username) {
		GroupInfo g = groups.get(groupId);
		if (g != null) {
			g.removeMember(username);
			saveGroupFile(g);
			notifyGroupUpdate();
			broadcastGroupList();
			log("Người dùng " + username + " đã rời nhóm " + g.getGroupName());
		}
	}

	public synchronized void deleteGroup(String groupId) {
		GroupInfo g = groups.remove(groupId);
		if (g != null) {
			// Xoá thư mục group tương ứng
			File groupDir = findGroupDirectory(groupId);
			if (groupDir != null && groupDir.exists()) {
				File[] files = groupDir.listFiles();
				if (files != null) {
					for (File file : files) {
						file.delete();
					}
				}
				groupDir.delete();
			}
			notifyGroupUpdate();
			broadcastGroupList();
			log("Đã giải tán nhóm: " + g.getGroupName() + " (" + groupId + ")");
		}
	}

	public void recordChat(
			ChatRecord.TargetType targetType, String sender, String target, String content) {
		ChatRecord record = new ChatRecord(targetType, sender, target, content);
		try {
			chatDatabase.save(record); // SAVE-CHAT: comment dòng này để ngừng thêm tin nhắn vào MySQL.
		} catch (java.sql.SQLException exception) {
			log("Không lưu được tin nhắn vào MySQL: " + exception.getMessage());
			throw new IllegalStateException("Không lưu được tin nhắn", exception);
		}
		chatHistory.add(record);
		if (listener != null) {
			listener.onMessageLogged(record);
		}
	}

	public void log(String message) {
		if (listener != null) {
			listener.onSystemLog(message);
		}
	}

	public void broadcastToAll(Message msg) {
		for (ClientHandler client : onlineClients.values()) {
			client.sendMessage(msg);
		}
	}

	public void broadcastUserList() {
		Message msg = new Message(Message.Type.USER_LIST_UPDATE);
		List<UserStatus> list = new ArrayList<>();
		for (String user : userAccounts.keySet()) {
			list.add(new UserStatus(user, onlineClients.containsKey(user)));
		}
		msg.setUserList(list);
		broadcastToAll(msg);
	}

	public void broadcastGroupList() {
		Message msg = new Message(Message.Type.GROUP_LIST_UPDATE);
		msg.setGroupList(new ArrayList<>(groups.values()));
		broadcastToAll(msg);
	}

	public void sendUserList(ClientHandler client) {
		Message msg = new Message(Message.Type.USER_LIST_UPDATE);
		List<UserStatus> list = new ArrayList<>();
		for (String user : userAccounts.keySet()) {
			list.add(new UserStatus(user, onlineClients.containsKey(user)));
		}
		msg.setUserList(list);
		client.sendMessage(msg);
	}

	public void sendGroupList(ClientHandler client) {
		Message msg = new Message(Message.Type.GROUP_LIST_UPDATE);
		msg.setGroupList(new ArrayList<>(groups.values()));
		client.sendMessage(msg);
	}

	private void notifyUserUpdate() {
		if (listener != null) {
			listener.onUserListUpdated(new HashMap<>(userAccounts), getOnlineUserIps());
		}
	}

	public Map<String, String> getOnlineUserIps() {
		Map<String, String> ips = new HashMap<>();
		for (Map.Entry<String, ClientHandler> entry : onlineClients.entrySet()) {
			ips.put(entry.getKey(), entry.getValue().getClientIp());
		}
		return ips;
	}

	private void notifyGroupUpdate() {
		if (listener != null) {
			listener.onGroupListUpdated(new HashMap<>(groups));
		}
	}

	// Getters for UI
	public Map<String, UserAccount> getUserAccounts() {
		return userAccounts;
	}

	public Set<String> getOnlineUsers() {
		return onlineClients.keySet();
	}

	public Map<String, GroupInfo> getGroups() {
		return groups;
	}

	public List<ChatRecord> getChatHistory() {
		return chatHistory;
	}
}
