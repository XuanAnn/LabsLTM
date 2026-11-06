package server;

import client.*;
import server.*;
import common.*;

import common.*;

import java.nio.file.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.sql.*;
import java.util.*;
import java.util.Map;
import java.util.concurrent.*;

/** Hai node giữ bản sao dữ liệu. Bản có version mới hơn sẽ được giữ khi đồng bộ. */
public final class Node extends UnicastRemoteObject implements Store {
	private final Db db;
	private final String id;

	public Node(String id, int exportPort, Path path) throws Exception {
		super(exportPort);
		this.id = id;
		db = new Db(path);

		db.exec("INSERT IGNORE INTO meta VALUES(1,?)", id);
		try (Connection connection = db.open();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT node FROM meta WHERE id=1")) {
			if (!resultSet.next() || !id.equals(resultSet.getString(1))) {
				throw new IllegalStateException("Database belongs to a different node ID");
			}
		}
	}

	public synchronized Map<String, Value> snapshot() throws RemoteException {
		try (Connection connection = db.open();
				Statement statement = connection.createStatement();
				ResultSet resultSet =
						statement.executeQuery(
								"SELECT entry_key,entry_value,version,origin,deleted FROM"
										+ " entries")) {
			Map<String, Value> valuesByKey = new HashMap<>();
			while (resultSet.next()) {
				String key = resultSet.getString(1);
				String text = resultSet.getString(2);
				long version = resultSet.getLong(3);
				String origin = resultSet.getString(4);
				boolean deleted = resultSet.getInt(5) != 0;
				Value value = new Value(text, version, origin, deleted);
				valuesByKey.put(key, value);
			}
			return valuesByKey;
		} catch (Exception exception) {
			throw new RemoteException("Read failed", exception);
		}
	}

	public synchronized Value put(String key, String text, boolean delete) throws RemoteException {
		if (key == null
				|| key.isBlank()
				|| key.length() > 256
				|| text == null
				|| text.length() > 65536) {
			throw new RemoteException("Invalid key/value");
		}
		try {
			// Mỗi lần sửa dùng version lớn hơn tất cả version đã biết trên node này.
			long version = nextVersion();
			String storedText = text;
			String action = "PUT ";
			if (delete) {
				storedText = "";
				action = "DELETE ";
			}
			Value newValue = new Value(storedText, version, id, delete);
			Map<String, Value> changedEntries = new HashMap<>();
			changedEntries.put(key, newValue);
			merge(changedEntries);
			db.audit(action + key + " version=" + version + " node=" + id);
			return newValue;
		} catch (Exception exception) {
			throw new RemoteException("Write failed", exception);
		}
	}

	private long nextVersion() throws RemoteException {
		long highestVersion = 0;
		for (Value value : snapshot().values()) {
			if (value.version() > highestVersion) {
				highestVersion = value.version();
			}
		}
		return Math.addExact(highestVersion, 1);
	}

	public synchronized void merge(Map<String, Value> incoming) throws RemoteException {
		if (!SaveOptions.enabled(SaveOptions.Kind.RMI_DATA)) {
			throw new RemoteException("Đang tắt ghi dữ liệu trong SaveOptions");
		}
		if (incoming == null || incoming.size() > 100000) {
			throw new RemoteException("Invalid batch");
		}
		Map<String, Value> current = snapshot();
		try (Connection connection = db.open()) {
			connection.setAutoCommit(false);
			try (PreparedStatement statement =
					connection.prepareStatement(
							"INSERT INTO entries(entry_key,entry_value,version,origin,deleted)"
								+ " VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE"
								+ " entry_value=VALUES(entry_value),version=VALUES(version),origin=VALUES(origin),deleted=VALUES(deleted)")) {
				for (Map.Entry<String, Store.Value> entry : incoming.entrySet()) {
					String key = entry.getKey();
					Value value = entry.getValue();
					validateEntry(key, value);
					Value old = current.get(key);
					// Không ghi đè bằng một bản cũ hơn hoặc giống hệt bản đang giữ.
					if (old != null && value.compare(old) <= 0) {
						continue;
					}
					addValueToBatch(statement, key, value);
				}
				// Chỉ commit khi toàn bộ nhóm thay đổi đã được ghi thành công.
				statement.executeBatch();
				connection.commit();
			}
		} catch (Exception exception) {
			throw new RemoteException("Merge failed", exception);
		}
	}

	private void validateEntry(String key, Value value) throws RemoteException {
		if (key == null || key.isBlank() || key.length() > 256) {
			throw new RemoteException("Invalid replicated entry");
		}
		if (value == null || value.text() == null || value.text().length() > 65536) {
			throw new RemoteException("Invalid replicated entry");
		}
		if (value.origin() == null
				|| !value.origin().matches("[A-Za-z0-9_-]{1,32}")
				|| value.version() < 1) {
			throw new RemoteException("Invalid replicated entry");
		}
	}

	private void addValueToBatch(PreparedStatement statement, String key, Value value)
			throws SQLException {
		int deletedFlag = 0;
		if (value.deleted()) {
			deletedFlag = 1;
		}
		statement.setString(1, key);
		statement.setString(2, value.text());
		statement.setLong(3, value.version());
		statement.setString(4, value.origin());
		statement.setInt(5, deletedFlag);
		statement.addBatch();
	}

	public String info() {
		return "Node " + id + " — eventual replication; version=(Lamport counter,node ID)";
	}

	public static AutoCloseable start(String[] arguments) throws Exception {
		// nodeId registryPort exportPort advertisedHost dbConfig [peerHost:registryPort]
		String id = Wire.arg(arguments, 0, "A");
		if (!id.matches("[A-Za-z0-9_-]{1,32}")) {
			throw new IllegalArgumentException("Invalid node ID");
		}
		int registry = Integer.parseInt(Wire.arg(arguments, 1, "1099")),
				export = Integer.parseInt(Wire.arg(arguments, 2, "1199"));
		System.setProperty("java.rmi.server.hostname", Wire.arg(arguments, 3, "127.0.0.1"));
		System.setProperty("sun.rmi.transport.tcp.responseTimeout", "5000");
		System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "5000");
		Node node =
				new Node(
						id,
						export,
						Path.of(
								Wire.arg(
										arguments,
										4,
										"config/database-"
												+ id.toLowerCase(Locale.ROOT)
												+ ".properties")));
		Registry reg;
		try {
			reg = LocateRegistry.createRegistry(registry);
			reg.rebind("Store", node);
		} catch (Exception exception) {
			UnicastRemoteObject.unexportObject(node, true);
			throw exception;
		}
		System.out.println(node.info() + " registry=" + registry + " export=" + export);
		ScheduledExecutorService sync = Executors.newSingleThreadScheduledExecutor();
		if (arguments.length > 5 && !arguments[5].isBlank()) {
			String[] peer = arguments[5].split(":");
			if (peer.length != 2) {
				throw new IllegalArgumentException("Peer must be host:registryPort");
			}
			sync.scheduleWithFixedDelay(
					() -> {
						try {
							Store remote =
									(Store)
											LocateRegistry.getRegistry(
															peer[0], Integer.parseInt(peer[1]))
													.lookup("Store");
							node.merge(remote.snapshot());
							remote.merge(node.snapshot());
						} catch (Exception exception2) {
							System.err.println(
									"Peer unavailable; retry in 2s: "
											+ exception2.getClass().getSimpleName());
						}
					},
					0,
					2,
					TimeUnit.SECONDS);
		}
		return () -> {
			sync.shutdownNow();
			try {
				UnicastRemoteObject.unexportObject(node, true);
			} finally {
				UnicastRemoteObject.unexportObject(reg, true);
			}
		};
	}

	public static void main(String[] arguments) throws Exception {
		start(arguments);
		new CountDownLatch(1).await();
	}
}
