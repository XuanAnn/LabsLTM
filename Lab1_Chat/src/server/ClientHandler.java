package server;

import common.ChatRecord;
import common.GroupInfo;
import common.Message;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
	private Socket socket;
	private ServerController server;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private String currentUsername = null;
	private volatile boolean isConnected = true;

	public ClientHandler(Socket socket, ServerController server) {
		this.socket = socket;
		this.server = server;
	}

	@Override
	public void run() {
		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());

			while (isConnected) {
				try {
					Object obj = in.readObject();
					if (obj instanceof Message) {
						handleMessage((Message) obj);
					}
				} catch (ClassNotFoundException exception) {
					server.log("Invalid object received: " + exception.getMessage());
				}
			}
		} catch (IOException exception2) {
			// Client disconnected or error
		} finally {
			closeConnection();
		}
	}

	private void handleMessage(Message msg) {
		switch (msg.getType()) {
			case LOGIN:
				handleLogin(msg);
				break;
			case REGISTER:
				handleRegister(msg);
				break;
			case LOGOUT:
				handleLogout();
				break;
			case CHAT_DIRECT:
				if (currentUsername != null) {
					msg.setSender(currentUsername);
					server.handleDirectMessage(msg);
				}
				break;
			case GROUP_CREATE:
				handleGroupCreate(msg);
				break;
			case GROUP_JOIN:
				if (currentUsername != null && msg.getContent() != null) {
					server.joinGroup(msg.getContent(), currentUsername);
				}
				break;
			case GROUP_LEAVE:
				if (currentUsername != null && msg.getContent() != null) {
					server.leaveGroup(msg.getContent(), currentUsername);
				}
				break;
			case CHAT_GROUP:
				// Server also records group message history for admin UI
				if (currentUsername != null) {
					server.recordChat(
							ChatRecord.TargetType.GROUP,
							currentUsername,
							msg.getReceiver(),
							msg.getContent());
				}
				break;
			case FILE_TRANSFER:
				if (currentUsername != null) {
					msg.setSender(currentUsername);
					server.handleFileBroadcast(msg);
				}
				break;
			default:
				break;
		}
	}

	private void handleLogin(Message msg) {
		if (currentUsername != null) {
			sendMessage(
					new Message(
							Message.Type.LOGIN_FAIL,
							"SERVER",
							currentUsername,
							"Please log out before changing account."));
			return;
		}
		String username = msg.getSender();
		String password = msg.getContent();

		if (server.authenticate(username, password)) {
			this.currentUsername = username;
			Message resp =
					new Message(Message.Type.LOGIN_SUCCESS, "SERVER", username, "Login successful");
			sendMessage(resp);
			server.addOnlineUser(username, this);

			// Send initial lists
			server.sendUserList(this);
			server.sendGroupList(this);
		} else {
			Message resp =
					new Message(
							Message.Type.LOGIN_FAIL,
							"SERVER",
							username,
							"Invalid username or password");
			sendMessage(resp);
		}
	}

	private void handleRegister(Message msg) {
		String username = msg.getSender();
		String password = msg.getContent();

		if (username == null
				|| username.trim().isEmpty()
				|| password == null
				|| password.trim().isEmpty()) {
			sendMessage(
					new Message(
							Message.Type.REGISTER_FAIL,
							"SERVER",
							username,
							"Username and password cannot be empty."));
			return;
		}

		if (server.registerAccount(username, password)) {
			sendMessage(
					new Message(
							Message.Type.REGISTER_SUCCESS,
							"SERVER",
							username,
							"Registration successful! You can now log in."));
		} else {
			sendMessage(
					new Message(
							Message.Type.REGISTER_FAIL,
							"SERVER",
							username,
							"Username already exists. Please choose another one."));
		}
	}

	private void handleGroupCreate(Message msg) {
		if (currentUsername == null) {
			return;
		}
		String groupName = msg.getContent();
		if (groupName != null && !groupName.trim().isEmpty()) {
			GroupInfo group = server.createNewGroup(groupName.trim(), currentUsername);
			server.broadcastGroupList();
			Message resp =
					new Message(
							Message.Type.GROUP_CREATE_SUCCESS,
							"SERVER",
							currentUsername,
							group.getGroupId());
			sendMessage(resp);
		} else {
			sendMessage(
					new Message(
							Message.Type.GROUP_CREATE_FAIL,
							"SERVER",
							currentUsername,
							"Group name cannot be empty."));
		}
	}

	private void handleLogout() {
		if (currentUsername != null) {
			server.removeOnlineUser(currentUsername, this);
			currentUsername = null;
		}
		closeConnection();
	}

	public void sendMessage(Message msg) {
		if (!isConnected || out == null) {
			return;
		}
		try {
			synchronized (out) {
				out.writeObject(msg);
				out.flush();
				out.reset();
			}
		} catch (IOException exception) {
			closeConnection();
		}
	}

	public void closeConnection() {
		String username;
		synchronized (this) {
			if (!isConnected) {
				return;
			}
			isConnected = false;
			username = currentUsername;
			currentUsername = null;
		}
		// Never call ServerController while holding a ClientHandler/write lock.
		// Closing the socket first also unblocks a concurrent readObject().
		try {
			socket.close();
		} catch (IOException ignored) {
		}
		if (username != null) {
			server.removeOnlineUser(username, this);
		}
		try {
			if (in != null) {
				in.close();
			}
		} catch (IOException ignored) {
		}
		try {
			if (out != null) {
				out.close();
			}
		} catch (IOException ignored) {
		}
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException ignored) {
		}
	}

	public String getCurrentUsername() {
		return currentUsername;
	}

	public String getClientAddress() {
		if (socket != null && socket.getRemoteSocketAddress() != null) {
			String addr = socket.getRemoteSocketAddress().toString();
			return addr.startsWith("/") ? addr.substring(1) : addr;
		}
		return "Unknown";
	}

	public String getClientIp() {
		if (socket != null && socket.getInetAddress() != null) {
			return socket.getInetAddress().getHostAddress();
		}
		return "Unknown";
	}
}
