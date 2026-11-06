package common;

import java.io.Serializable;

public class UserStatus implements Serializable {
	private static final long serialVersionUID = 1L;

	private String username;
	private boolean online;

	public UserStatus(String username, boolean online) {
		this.username = username;
		this.online = online;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public boolean isOnline() {
		return online;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

	@Override
	public String toString() {
		return username + (online ? " (Online)" : " (Offline)");
	}
}
