package common;

import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Type {
		// Auth
		LOGIN,
		LOGIN_SUCCESS,
		LOGIN_FAIL,
		REGISTER,
		REGISTER_SUCCESS,
		REGISTER_FAIL,
		LOGOUT,

		// User list / Status
		USER_LIST_UPDATE, // list of all accounts + online status

		// Chat 1-1
		CHAT_DIRECT,

		// Group Chat
		GROUP_CREATE,
		GROUP_CREATE_SUCCESS,
		GROUP_CREATE_FAIL,
		GROUP_JOIN,
		GROUP_LEAVE,
		GROUP_LIST_UPDATE,
		CHAT_GROUP,

		// System broadcast / notification
		SYSTEM_NOTIFICATION,

		// File transfer (bonus feature)
		FILE_TRANSFER
	}

	private Type type;
	private String sender;
	private String receiver; // username if direct, or groupId/groupName if group
	private String content;
	private long timestamp;

	// Extra payloads
	private List<UserStatus> userList;
	private List<GroupInfo> groupList;
	private byte[] fileData;
	private String fileName;

	public Message(Type type) {
		this.type = type;
		this.timestamp = System.currentTimeMillis();
	}

	public Message(Type type, String sender, String receiver, String content) {
		this.type = type;
		this.sender = sender;
		this.receiver = receiver;
		this.content = content;
		this.timestamp = System.currentTimeMillis();
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getReceiver() {
		return receiver;
	}

	public void setReceiver(String receiver) {
		this.receiver = receiver;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public List<UserStatus> getUserList() {
		return userList;
	}

	public void setUserList(List<UserStatus> userList) {
		this.userList = userList;
	}

	public List<GroupInfo> getGroupList() {
		return groupList;
	}

	public void setGroupList(List<GroupInfo> groupList) {
		this.groupList = groupList;
	}

	public byte[] getFileData() {
		return fileData;
	}

	public void setFileData(byte[] fileData) {
		this.fileData = fileData;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	@Override
	public String toString() {
		return "[" + type + "] " + sender + " -> " + receiver + ": " + content;
	}
}
