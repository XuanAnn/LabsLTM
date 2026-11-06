package server;

import client.*;
import server.*;
import common.*;

import common.Db;
import common.SaveOptions;

import java.util.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Application visits persist across the short HTTP TCP connections. */
final class WebSessions {
	private final Db db;
	private final Set<String> owned = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> memoryActivity = new ConcurrentHashMap<>();

	WebSessions(Db db) {
		this.db = db;
	}

	String resolve(String id, String ip, boolean create) throws Exception {
		// Khi tắt lưu SESSION, vẫn giữ phiên trong RAM để client tiếp tục hoạt động.
		if (!SaveOptions.enabled(SaveOptions.Kind.SESSION)) {
			if (id != null && owned.contains(id)) {
				memoryActivity.put(id, System.currentTimeMillis());
				return id;
			}
			if (!create) {
				return null;
			}
			id = UUID.randomUUID().toString();
			owned.add(id);
			memoryActivity.put(id, System.currentTimeMillis());
			return id;
		}
		if (id != null
				&& id.matches("[0-9a-f-]{36}")
				&& !db.rows(
								"SELECT id FROM sessions WHERE id=? AND protocol='HTTP' AND"
										+ " disconnected_at IS NULL",
								id)
						.isEmpty()) {
			db.exec("UPDATE sessions SET last_seen=NOW() WHERE id=?", id);
			owned.add(id);
			return id;
		}
		if (!create) {
			return null;
		}
		id = UUID.randomUUID().toString();
		db.exec(
				"INSERT INTO sessions(id,username,remote_ip,protocol,last_seen)"
						+ " VALUES(?,?,?,'HTTP',NOW())",
				id,
				"Trình duyệt",
				ip);
		owned.add(id);
		db.audit("KẾT NỐI " + ip + " · " + id);
		return id;
	}

	void end(String id) throws Exception {
		if (id != null) {
			if (SaveOptions.enabled(SaveOptions.Kind.SESSION)) {
				db.exec(
						"UPDATE sessions SET disconnected_at=NOW(),last_seen=NOW() WHERE id=? AND"
								+ " disconnected_at IS NULL",
						id);
			}
			owned.remove(id);
			memoryActivity.remove(id);
		}
	}

	void expire() throws Exception {
		if (SaveOptions.enabled(SaveOptions.Kind.SESSION)) {
			db.exec(
					"UPDATE sessions SET disconnected_at=NOW() WHERE protocol='HTTP' AND"
							+ " disconnected_at IS NULL AND last_seen<DATE_SUB(NOW(),INTERVAL 45"
							+ " SECOND)");
		} else {
			for (Map.Entry<String, Long> entry : memoryActivity.entrySet()) {
				if (System.currentTimeMillis() - entry.getValue() > 45000) {
					end(entry.getKey());
				}
			}
		}
	}

	void close() throws Exception {
		for (String id : owned) {
			end(id);
		}
	}

	static String token(Map<String, String> headers) {
		String id = headers.get("x-lab-session");
		if (id != null) {
			return id;
		}
		for (String cookie : headers.getOrDefault("cookie", "").split(";")) {
			String[] parts = cookie.trim().split("=", 2);
			if (parts.length == 2 && parts[0].equals("LTM_SESSION")) {
				return parts[1];
			}
		}
		return null;
	}
}
