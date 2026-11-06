package common;

import client.*;
import server.*;
import common.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Dashboard được chia thành các hàm tạo giao diện và các hàm xử lý thao tác. */
public class AdminFrame extends JFrame {
	public record Settings(
			Path config, int port, int secondPort, String node, String advertised, String peer) {}

	public interface Service {
		void start(Settings settings) throws Exception;

		void stop() throws Exception;

		boolean running();
	}

	public record DataTab(String title, String[] columns, String sql) {}

	private final Service service;
	private final String type;
	private volatile Db db;
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private final JTextField config = DesktopUi.field("config/database.properties", 28);
	private final JTextField dbHost = DesktopUi.field("127.0.0.1", 15);
	private final JTextField dbPort = DesktopUi.field("3306", 6);
	private final JTextField dbName = DesktopUi.field("", 18);
	private final JTextField dbUser = DesktopUi.field("root", 12);
	private final JTextField dbPassword = DesktopUi.field("", 12);
	private final JTextField port = DesktopUi.field("", 5);
	private final JTextField second = DesktopUi.field("", 5);
	private final JTextField node = DesktopUi.field("A", 3);
	private final JTextField advertised = DesktopUi.field("127.0.0.1", 10);
	private final JTextField peer = DesktopUi.field("127.0.0.1:2099", 16);
	private final JButton start = DesktopUi.button("Khởi động Server", true);
	private final JButton addButton = DesktopUi.button("+ Thêm tài khoản", true);
	private final JButton resetButton = DesktopUi.button("Đổi mật khẩu", false);
	private final JButton removeButton = DesktopUi.button("Xóa tài khoản", false);
	private final JButton saveConfigButton = DesktopUi.button("Lưu và kiểm tra", true);
	private final JButton loadConfigButton = DesktopUi.button("Nạp file", false);
	private final JLabel state = DesktopUi.tag("ĐÃ DỪNG", false);
	private final JLabel dbState = DesktopUi.tag("Chưa kết nối dữ liệu", false);
	private final JLabel accountCount = new JLabel("0");
	private final JLabel activeCount = new JLabel("0");
	private final JLabel dataCount = new JLabel("0");
	private final JLabel auditCount = new JLabel("0");
	private final DefaultTableModel accounts =
			DesktopUi.model("Tài khoản", "Email", "Mật khẩu", "Tạo lúc", "Đăng nhập", "Đăng xuất");
	private final JTable accountTable = DesktopUi.table(accounts);
	private final List<DefaultTableModel> models = new ArrayList<>();
	private final List<String> queries = new ArrayList<>();
	private final JTextArea logs = new JTextArea();
	private final Timer timer;

	public AdminFrame(
			String title,
			String protocol,
			String type,
			int defaultPort,
			int otherPort,
			Service service,
			DataTab... tabs) {
		super(title);
		this.type = type;
		this.service = service;
		DesktopUi.init();
		DesktopUi.window(this);
		port.setText(String.valueOf(defaultPort));
		second.setText(String.valueOf(otherPort));
		loadConfig();
		add(createHeader(title, otherPort), BorderLayout.NORTH);
		add(createMainPanel(tabs), BorderLayout.CENTER);
		registerEvents();
		timer = new Timer(2000, event -> refresh());
		timer.start();
	}

	private JPanel createHeader(String title, int otherPort) {
		JPanel controls = DesktopUi.row(new JLabel("Cổng"), port);
		if (otherPort > 0) {
			controls.add(new JLabel("Cổng nhận"));
			controls.add(second);
		}
		controls.add(start);
		controls.add(state);
		return DesktopUi.header(title, "Quản lý hoạt động và kết nối", controls);
	}

	private JPanel createMainPanel(DataTab[] dataTabs) {
		JPanel root = new JPanel(new BorderLayout(0, 14));
		root.setBackground(AntTheme.BG);
		root.setBorder(BorderFactory.createEmptyBorder(14, 18, 18, 18));
		root.add(createStatisticsPanel(), BorderLayout.NORTH);
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Tài khoản", createAccountsPanel());
		models.add(accounts);
		queries.add(
				"SELECT name,email,password,created_at,last_login,last_logout FROM users ORDER BY"
					+ " name");
		for (DataTab dataTab : dataTabs) {
			addDataTab(tabs, dataTab.title(), DesktopUi.model(dataTab.columns()), dataTab.sql());
		}
		addSessionTab(tabs);
		addDataTab(
				tabs,
				"Nhật ký dữ liệu",
				DesktopUi.model("ID", "Thời gian", "Sự kiện"),
				"SELECT id,at,event FROM audit ORDER BY id DESC LIMIT 300");
		tabs.addTab("Cấu hình dữ liệu", createConfigurationPanel());
		logs.setEditable(false);
		logs.setFont(new Font("Consolas", Font.PLAIN, 13));
		logs.setBackground(new Color(0x141414));
		logs.setForeground(new Color(0xE6F4FF));
		tabs.addTab("Nhật ký", new JScrollPane(logs));
		root.add(tabs, BorderLayout.CENTER);
		return root;
	}

	private JPanel createStatisticsPanel() {
		JPanel panel = new JPanel(new GridLayout(1, 4, 12, 0));
		panel.setOpaque(false);
		panel.add(createStatistic("Phiên đang kết nối", activeCount));
		panel.add(createStatistic("Tổng tài khoản", accountCount));
		panel.add(createStatistic("Dữ liệu dịch vụ", dataCount));
		panel.add(createStatistic("Nhật ký ghi nhận", auditCount));
		return panel;
	}

	private JPanel createStatistic(String title, JLabel value) {
		JPanel panel = DesktopUi.card(title);
		value.setFont(new Font("Segoe UI", Font.BOLD, 28));
		value.setForeground(AntTheme.BLUE);
		panel.add(value, BorderLayout.CENTER);
		return panel;
	}

	private JPanel createAccountsPanel() {
		JPanel panel = DesktopUi.card(null);
		panel.add(new JScrollPane(accountTable), BorderLayout.CENTER);
		panel.add(DesktopUi.row(addButton, resetButton, removeButton), BorderLayout.SOUTH);
		return panel;
	}

	private void addDataTab(JTabbedPane tabs, String title, DefaultTableModel model, String query) {
		models.add(model);
		queries.add(query);
		tabs.addTab(title, new JScrollPane(DesktopUi.table(model)));
	}

	private void addSessionTab(JTabbedPane tabs) {
		DefaultTableModel model =
				DesktopUi.model("Tài khoản", "IP Client", "Giao thức", "Vào lúc", "Ra lúc");
		addDataTab(
				tabs,
				"Kết nối & IP",
				model,
				"SELECT username,remote_ip,protocol,connected_at,disconnected_at FROM sessions"
					+ " ORDER BY connected_at DESC LIMIT 300");
	}

	private JPanel createConfigurationPanel() {
		JPanel panel = DesktopUi.card("Kết nối MySQL — Laragon / HeidiSQL");
		String[] labels = {
			"File cấu hình",
			"Địa chỉ dữ liệu",
			"Cổng dữ liệu",
			"Tên cơ sở dữ liệu",
			"Tài khoản dữ liệu",
			"Mật khẩu dữ liệu"
		};
		JComponent[] fields = {config, dbHost, dbPort, dbName, dbUser, dbPassword};
		JPanel form = DesktopUi.form(labels, fields);

		panel.add(form, BorderLayout.NORTH);
		panel.add(DesktopUi.row(saveConfigButton, loadConfigButton, dbState), BorderLayout.SOUTH);
		return panel;
	}

	private void registerEvents() {
		start.addActionListener(event -> DesktopUi.work(this, start, this::toggleService));
		saveConfigButton.addActionListener(
				event -> DesktopUi.work(this, saveConfigButton, this::checkDatabase));
		loadConfigButton.addActionListener(event -> loadConfig());
		addButton.addActionListener(event -> addAccount());
		resetButton.addActionListener(event -> resetPassword());
		removeButton.addActionListener(event -> removeAccount());
		addWindowListener(
				new WindowAdapter() {
					@Override
					public void windowClosed(WindowEvent event) {
						timer.stop();
						Thread.startVirtualThread(() -> stopServiceOnClose());
					}
				});
	}

	private void toggleService() throws Exception {
		if (service.running()) {
			service.stop();
			DesktopUi.log(logs, "Server đã dừng");
		} else {
			saveConfig();
			Path path = LabPaths.resolve(Path.of(DesktopUi.value(config)));
			db = new Db(path);
			// Những phiên của lần chạy trước không còn được xem là đang kết nối.
			db.exec("UPDATE sessions SET disconnected_at=NOW() WHERE disconnected_at IS NULL");
			System.setProperty("lab.db.config", path.toString());
			Settings settings =
					new Settings(
							path,
							Integer.parseInt(DesktopUi.value(port)),
							Integer.parseInt(DesktopUi.value(second)),
							DesktopUi.value(node),
							DesktopUi.value(advertised),
							DesktopUi.value(peer));
			service.start(settings);
			DesktopUi.log(logs, "Khởi động " + type + " với schema " + db.name());
		}
		refresh();
	}

	private void stopServiceOnClose() {
		try {
			service.stop();
		} catch (Exception exception) {
			DesktopUi.log(logs, exception.getMessage());
		}
	}

	private void checkDatabase() throws Exception {
		saveConfig();
		db = new Db(LabPaths.resolve(Path.of(DesktopUi.value(config))));
		DesktopUi.log(logs, "Đã kết nối: " + db.name());
		refresh();
	}

	private void addAccount() {
		JTextField username = DesktopUi.field("", 15);
		JTextField password = DesktopUi.field("123", 15);
		JTextField email = DesktopUi.field("", 25);
		JPanel form =
				DesktopUi.form(
						new String[] {"Tài khoản", "Email", "Mật khẩu"},
						new JComponent[] {username, email, password});
		if (JOptionPane.showConfirmDialog(
						this, form, "Thêm tài khoản", JOptionPane.OK_CANCEL_OPTION)
				!= JOptionPane.OK_OPTION) {
			return;
		}
		String name = DesktopUi.value(username);
		String pass = DesktopUi.value(password);
		String emailAddress = DesktopUi.value(email);
		DesktopUi.work(
				this,
				addButton,
				() -> {
					requireDb();
					if (name.isBlank() || pass.isBlank()) {
						throw new IOException("Nhập tài khoản và mật khẩu");
					}
					if (!db.register(name, pass, emailAddress)) {
						throw new IOException("Tài khoản đã tồn tại");
					}
					refresh();
				});
	}

	private void resetPassword() {
		String username = selectedUser();
		if (username == null) {
			return;
		}
		String password = JOptionPane.showInputDialog(this, "Mật khẩu mới cho " + username);
		if (password != null) {
			DesktopUi.work(
					this,
					resetButton,
					() -> {
						requireDb();
						db.exec("UPDATE users SET password=? WHERE name=?", password, username);
						refresh();
					});
		}
	}

	private void removeAccount() {
		String username = selectedUser();
		if (username == null) {
			return;
		}
		int answer =
				JOptionPane.showConfirmDialog(
						this,
						"Xóa tài khoản " + username + "?",
						"Tài khoản",
						JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION) {
			DesktopUi.work(
					this,
					removeButton,
					() -> {
						requireDb();
						db.exec("DELETE FROM users WHERE name=?", username);
						refresh();
					});
		}
	}

	private String selectedUser() {
		int row = accountTable.getSelectedRow();
		if (row < 0) {
			return null;
		}
		return String.valueOf(accountTable.getValueAt(row, 0));
	}

	private void requireDb() throws IOException {
		if (db == null) {
			throw new IOException("Kết nối dữ liệu trước trong tab cấu hình");
		}
	}

	private void loadConfig() {
		try {
			Properties settings = new Properties();
			try (Reader reader = Files.newBufferedReader(LabPaths.resolve(Path.of(config.getText())))) {
				settings.load(reader);
			}
			dbHost.setText(settings.getProperty("host", "127.0.0.1"));
			dbPort.setText(settings.getProperty("port", "3306"));
			dbName.setText(settings.getProperty("database", ""));
			dbUser.setText(settings.getProperty("user", "root"));
			dbPassword.setText(settings.getProperty("password", ""));
		} catch (Exception exception) {
			DesktopUi.log(logs, exception.getMessage());
		}
	}

	private void saveConfig() throws Exception {
		Properties settings = new Properties();
		settings.setProperty("host", DesktopUi.value(dbHost));
		settings.setProperty("port", DesktopUi.value(dbPort));
		settings.setProperty("database", DesktopUi.value(dbName));
		settings.setProperty("user", DesktopUi.value(dbUser));
		settings.setProperty("password", DesktopUi.value(dbPassword));
		Path path = LabPaths.resolve(Path.of(DesktopUi.value(config)));
		Files.createDirectories(path.toAbsolutePath().getParent());
		try (Writer writer = Files.newBufferedWriter(path)) {
			settings.store(writer, "MySQL configuration for this lab");
		}
	}

	private void refresh() {
		SwingUtilities.invokeLater(this::showServerState);
		if (db == null || !refreshing.compareAndSet(false, true)) {
			return;
		}
		Thread.startVirtualThread(this::loadDashboard);
	}

	private void showServerState() {
		if (service.running()) {
			start.setText("Dừng Server");
			state.setText("ĐANG CHẠY");
			state.setForeground(AntDesignTheme.SUCCESS);
		} else {
			start.setText("Khởi động Server");
			state.setText("ĐÃ DỪNG");
			state.setForeground(AntDesignTheme.ERROR);
		}
	}

	private void loadDashboard() {
		try {
			Db current = db;
			for (int index = 0; index < models.size(); index++) {
				List<Object[]> rows = current.rows(queries.get(index));
				DesktopUi.rows(models.get(index), rows);
			}
			long users = current.count("users");
			long audits = current.count("audit");
			List<Object[]> activeRows =
					current.rows("SELECT COUNT(*) FROM sessions WHERE disconnected_at IS NULL");
			long active = ((Number) activeRows.get(0)[0]).longValue();
			long records = current.count("mail");
			SwingUtilities.invokeLater(
					() -> showCounters(users, audits, active, records, current.name()));
		} catch (Exception exception) {
			DesktopUi.log(logs, "Kết nối dữ liệu: " + exception.getMessage());
		} finally {
			refreshing.set(false);
		}
	}

	private void showCounters(long users, long audits, long active, long records, String database) {
		accountCount.setText(String.valueOf(users));
		auditCount.setText(String.valueOf(audits));
		activeCount.setText(String.valueOf(active));
		dataCount.setText(String.valueOf(records));
		dbState.setText("Đã kết nối: " + database);
		dbState.setForeground(AntDesignTheme.SUCCESS);
	}
}
