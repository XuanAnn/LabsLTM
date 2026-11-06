package server;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UserAccount implements Serializable {
	private static final long serialVersionUID = 1L;

	private String username;
	private String password;
	private String createdAt;
	private String lastLoginTime = "-";
	private String lastLogoutTime = "-";

	public UserAccount(String username, String password) {
		this.username = username;
		this.password = password;
		this.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
	}

	public UserAccount(String username, String password, String createdAt) {
		this.username = username;
		this.password = password;
		this.createdAt =
				createdAt != null
						? createdAt
						: new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
	}

	public UserAccount(
			String username,
			String password,
			String createdAt,
			String lastLoginTime,
			String lastLogoutTime) {
		this.username = username;
		this.password = password;
		this.createdAt =
				createdAt != null
						? createdAt
						: new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		this.lastLoginTime = lastLoginTime != null ? lastLoginTime : "-";
		this.lastLogoutTime = lastLogoutTime != null ? lastLogoutTime : "-";
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getLastLoginTime() {
		return lastLoginTime;
	}

	public void setLastLoginTime(String lastLoginTime) {
		this.lastLoginTime = lastLoginTime;
	}

	public String getLastLogoutTime() {
		return lastLogoutTime;
	}

	public void setLastLogoutTime(String lastLogoutTime) {
		this.lastLogoutTime = lastLogoutTime;
	}
}
