package client;

import common.GroupInfo;
import common.Message;
import common.UserStatus;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientController {
	public interface ClientEventListener {
		void onLoginResult(boolean success, String message);

		void onRegisterResult(boolean success, String message);

		void onUserListUpdated(List<UserStatus> users);

		void onGroupListUpdated(List<GroupInfo> groups);

		void onDirectMessageReceived(String sender, String message, long timestamp);

		void onGroupMessageReceived(String groupId, String sender, String message, long timestamp);

		void onFileReceived(String sender, String fileName, byte[] fileData, long timestamp);

		void onSystemNotification(String notification);

		void onConnectionClosed(String reason);
	}

	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private boolean isConnected = false;
	private String currentUsername;
	private String connectedHost = "127.0.0.1";
	private int connectedPort = 8888;

	private ClientEventListener listener;

	// Multicast listeners: groupId -> MulticastReceiverThread
	private final Map<String, MulticastReceiverThread> activeMulticastReceivers =
			new ConcurrentHashMap<>();
	private final Map<String, GroupInfo> joinedGroups = new ConcurrentHashMap<>();

	public ClientController(ClientEventListener listener) {
		this.listener = listener;
	}

	public boolean connect(String host, int port) {
		try {
			socket = new Socket(host, port);
			this.connectedHost = host;
			this.connectedPort = port;
			out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());
			isConnected = true;

			// Start listening thread
			new Thread(this::listenToServer).start();
			return true;
		} catch (IOException exception) {
			isConnected = false;
			return false;
		}
	}

	public void login(String username, String password) {
		this.currentUsername = username;
		Message msg = new Message(Message.Type.LOGIN, username, "SERVER", password);
		send(msg);
	}

	public void register(String username, String password) {
		Message msg = new Message(Message.Type.REGISTER, username, "SERVER", password);
		send(msg);
	}

	public void sendDirectMessage(String receiver, String text) {
		Message msg = new Message(Message.Type.CHAT_DIRECT, currentUsername, receiver, text);
		send(msg);
	}

	public void broadcastFile(File file) {
		if (file == null || !file.exists() || !file.isFile()) {
			return;
		}
		try {
			byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
			Message msg =
					new Message(
							Message.Type.FILE_TRANSFER,
							currentUsername,
							"ALL",
							"Broadcast File: " + file.getName());
			msg.setFileName(file.getName());
			msg.setFileData(data);
			send(msg);
		} catch (IOException exception) {
			System.err.println("Lỗi đọc file: " + exception.getMessage());
		}
	}

	public void createGroup(String groupName) {
		Message msg = new Message(Message.Type.GROUP_CREATE, currentUsername, "SERVER", groupName);
		send(msg);
	}

	public void requestJoinGroup(GroupInfo group) {
		// Send join request to server so server updates member list
		Message msg =
				new Message(Message.Type.GROUP_JOIN, currentUsername, "SERVER", group.getGroupId());
		send(msg);
		joinMulticastGroup(group);
	}

	public void requestLeaveGroup(GroupInfo group) {
		Message msg =
				new Message(
						Message.Type.GROUP_LEAVE, currentUsername, "SERVER", group.getGroupId());
		send(msg);
		leaveMulticastGroup(group.getGroupId());
	}

	// Multicast join
	public synchronized void joinMulticastGroup(GroupInfo group) {
		if (activeMulticastReceivers.containsKey(group.getGroupId())) {
			return; // Already joined
		}
		joinedGroups.put(group.getGroupId(), group);
		MulticastReceiverThread receiver = new MulticastReceiverThread(group);
		activeMulticastReceivers.put(group.getGroupId(), receiver);
		receiver.start();
	}

	public synchronized void leaveMulticastGroup(String groupId) {
		MulticastReceiverThread receiver = activeMulticastReceivers.remove(groupId);
		if (receiver != null) {
			receiver.stopListening();
		}
		joinedGroups.remove(groupId);
	}

	// Send multicast message
	public void sendGroupMessage(GroupInfo group, String messageText) {
		// 1. Send via Multicast UDP to group participants
		try {
			String payload = group.getGroupId() + "\n" + currentUsername + "\n" + messageText;
			byte[] buffer = payload.getBytes(StandardCharsets.UTF_8);
			InetAddress mcastGroup = InetAddress.getByName(group.getMulticastAddress());

			try (MulticastSocket udpSocket = new MulticastSocket()) {
				udpSocket.setNetworkInterface(multicastInterface(group));
				udpSocket.setTimeToLive(1);
				DatagramPacket packet =
						new DatagramPacket(
								buffer, buffer.length, mcastGroup, group.getMulticastPort());
				udpSocket.send(packet);
			}
		} catch (Exception exception) {
			System.err.println("Error sending multicast message: " + exception.getMessage());
		}

		// 2. Also inform server so server can log this group message on admin monitor
		Message serverLogMsg =
				new Message(
						Message.Type.CHAT_GROUP,
						currentUsername,
						group.getGroupName(),
						messageText);
		send(serverLogMsg);
	}

	private void listenToServer() {
		try {
			while (isConnected) {
				try {
					Object obj = in.readObject();
					if (obj instanceof Message) {
						handleServerMessage((Message) obj);
					}
				} catch (ClassNotFoundException exception) {
					System.err.println("Unknown object: " + exception.getMessage());
				}
			}
		} catch (IOException exception2) {
			if (isConnected) {
				disconnect("Connection to server lost.");
			}
		}
	}

	private void handleServerMessage(Message msg) {
		switch (msg.getType()) {
			case LOGIN_SUCCESS:
				if (listener != null) {
					listener.onLoginResult(true, msg.getContent());
				}
				break;
			case LOGIN_FAIL:
				if (listener != null) {
					listener.onLoginResult(false, msg.getContent());
				}
				break;
			case REGISTER_SUCCESS:
				if (listener != null) {
					listener.onRegisterResult(true, msg.getContent());
				}
				break;
			case REGISTER_FAIL:
				if (listener != null) {
					listener.onRegisterResult(false, msg.getContent());
				}
				break;
			case USER_LIST_UPDATE:
				if (listener != null && msg.getUserList() != null) {
					listener.onUserListUpdated(msg.getUserList());
				}
				break;
			case GROUP_LIST_UPDATE:
				if (listener != null && msg.getGroupList() != null) {
					listener.onGroupListUpdated(msg.getGroupList());
					// Auto-join groups where current user is a member
					if (currentUsername != null) {
						for (GroupInfo g : msg.getGroupList()) {
							if (g.hasMember(currentUsername)) {
								joinMulticastGroup(g);
							}
						}
					}
				}
				break;
			case CHAT_DIRECT:
				if (listener != null) {
					listener.onDirectMessageReceived(
							msg.getSender(), msg.getContent(), msg.getTimestamp());
				}
				break;
			case FILE_TRANSFER:
				if (listener != null) {
					listener.onFileReceived(
							msg.getSender(),
							msg.getFileName(),
							msg.getFileData(),
							msg.getTimestamp());
				}
				break;
			case SYSTEM_NOTIFICATION:
				if (listener != null) {
					listener.onSystemNotification(msg.getContent());
				}
				break;
			default:
				break;
		}
	}

	private synchronized void send(Message msg) {
		if (!isConnected || out == null) {
			return;
		}
		try {
			out.writeObject(msg);
			out.flush();
			out.reset();
		} catch (IOException exception) {
			disconnect("Failed to send message: " + exception.getMessage());
		}
	}

	public synchronized void disconnect(String reason) {
		if (!isConnected) {
			return;
		}
		isConnected = false;

		// Stop all multicast receivers
		for (MulticastReceiverThread receiver : activeMulticastReceivers.values()) {
			receiver.stopListening();
		}
		activeMulticastReceivers.clear();
		joinedGroups.clear();

		try {
			if (out != null) {
				out.writeObject(new Message(Message.Type.LOGOUT, currentUsername, "SERVER", ""));
				out.flush();
			}
		} catch (Exception ignored) {
		}

		try {
			if (in != null) {
				in.close();
			}
		} catch (Exception ignored) {
		}
		try {
			if (out != null) {
				out.close();
			}
		} catch (Exception ignored) {
		}
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (Exception ignored) {
		}

		if (listener != null) {
			listener.onConnectionClosed(reason);
		}
	}

	public boolean isConnected() {
		return isConnected;
	}

	public String getCurrentUsername() {
		return currentUsername;
	}

	public boolean isJoinedGroup(String groupId) {
		return activeMulticastReceivers.containsKey(groupId);
	}

	public String getServerAddress() {
		if (socket != null && socket.getRemoteSocketAddress() != null) {
			String addr = socket.getRemoteSocketAddress().toString();
			return addr.startsWith("/") ? addr.substring(1) : addr;
		}
		return connectedHost + ":" + connectedPort;
	}

	public String getLocalAddress() {
		if (socket != null && socket.getLocalSocketAddress() != null) {
			return socket.getLocalAddress().getHostAddress();
		}
		return "Unknown";
	}

	// Inner class for receiving Multicast packets
	private NetworkInterface multicastInterface(GroupInfo group) throws IOException {
		NetworkInterface candidate =
				socket == null ? null : NetworkInterface.getByInetAddress(socket.getLocalAddress());
		if (validMulticastInterface(candidate)) {
			return candidate;
		}
		// Determine the route without transmitting a datagram; localhost TCP has no LAN interface.
		try (DatagramSocket probe = new DatagramSocket()) {
			probe.connect(
					InetAddress.getByName(group.getMulticastAddress()), group.getMulticastPort());
			candidate = NetworkInterface.getByInetAddress(probe.getLocalAddress());
			if (validMulticastInterface(candidate)) {
				return candidate;
			}
		}
		for (NetworkInterface networkInterface :
				Collections.list(NetworkInterface.getNetworkInterfaces())) {
			if (validMulticastInterface(networkInterface)) {
				return networkInterface;
			}
		}
		throw new SocketException("No active IPv4 multicast interface");
	}

	private boolean validMulticastInterface(NetworkInterface networkInterface)
			throws SocketException {
		if (networkInterface == null) {
			return false;
		}
		if (!networkInterface.isUp()
				|| networkInterface.isLoopback()
				|| !networkInterface.supportsMulticast()) {
			return false;
		}
		// Nhóm multicast của lab dùng IPv4, nên card mạng phải có ít nhất một địa chỉ IPv4.
		Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
		while (addresses.hasMoreElements()) {
			InetAddress address = addresses.nextElement();
			if (address instanceof Inet4Address) {
				return true;
			}
		}
		return false;
	}

	private class MulticastReceiverThread extends Thread {
		private GroupInfo group;
		private MulticastSocket mcastSocket;
		private NetworkInterface joinedInterface;
		private volatile boolean running = true;

		public MulticastReceiverThread(GroupInfo group) {
			this.group = group;
			setName("McastReceiver-" + group.getGroupName());
		}

		@Override
		public void run() {
			try {
				InetAddress mcastAddr = InetAddress.getByName(group.getMulticastAddress());
				mcastSocket = new MulticastSocket(null);
				mcastSocket.setReuseAddress(true);
				mcastSocket.bind(new InetSocketAddress(group.getMulticastPort()));

				// Modern Java multicast join
				SocketAddress groupAddress =
						new InetSocketAddress(mcastAddr, group.getMulticastPort());
				joinedInterface = multicastInterface(group);
				mcastSocket.joinGroup(groupAddress, joinedInterface);

				byte[] buffer = new byte[65535];
				while (running) {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					mcastSocket.receive(packet);

					String data =
							new String(
									packet.getData(),
									0,
									packet.getLength(),
									StandardCharsets.UTF_8);
					String[] lines = data.split("\n", 3);
					if (lines.length >= 3) {
						String gId = lines[0];
						String sender = lines[1];
						String message = lines[2];

						if (listener != null && gId.equals(group.getGroupId())) {
							listener.onGroupMessageReceived(
									gId, sender, message, System.currentTimeMillis());
						}
					}
				}
			} catch (SocketException exception) {
				// Socket closed normally on leave
			} catch (Exception exception2) {
				if (running) {
					System.err.println(
							"Multicast error on group "
									+ group.getGroupName()
									+ ": "
									+ exception2.getMessage());
				}
			} finally {
				stopListening();
			}
		}

		public void stopListening() {
			running = false;
			if (mcastSocket != null && !mcastSocket.isClosed()) {
				try {
					InetAddress mcastAddr = InetAddress.getByName(group.getMulticastAddress());
					mcastSocket.leaveGroup(
							new InetSocketAddress(mcastAddr, group.getMulticastPort()),
							joinedInterface);
				} catch (Exception ignored) {
				}
				mcastSocket.close();
			}
		}
	}
}
