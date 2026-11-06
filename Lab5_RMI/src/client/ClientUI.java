package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.awt.*;
import java.awt.event.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class ClientUI {
	private final ClientWindow ui = new ClientWindow("Dữ liệu đồng bộ", 1099, 0, false);
	private final ClientController controller = new ClientController();
	private final JTextField key = DesktopUi.field("student:001", 25);
	private final JTextArea value = new JTextArea(5, 40);
	private final DefaultTableModel model =
			DesktopUi.model("Mã", "Nội dung", "Version", "Máy chủ", "Đã xóa");
	private final JTable table = DesktopUi.table(model);
	private final JLabel status = DesktopUi.tag("Chưa kết nối", false);
	private final JButton refreshButton = DesktopUi.button("Làm mới", true);
	private final JButton saveButton = DesktopUi.button("Lưu bản ghi", true);
	private final JButton deleteButton = DesktopUi.button("Xóa bản ghi", false);
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private Timer timer;

	private ClientUI(String[] arguments) {
		ui.host.setText(Wire.arg(arguments, 0, "127.0.0.1"));
		ui.port.setText(Wire.arg(arguments, 1, "1099"));
		registerEvents();
		ui.setVisible(true);
	}

	private void registerEvents() {
		ui.login.addActionListener(event -> connectToServer());
		refreshButton.addActionListener(event -> refresh());
		saveButton.addActionListener(event -> saveEntry());
		deleteButton.addActionListener(event -> deleteEntry());
		table.getSelectionModel()
				.addListSelectionListener(
						event -> {
							if (!event.getValueIsAdjusting()) {
								selectEntry();
							}
						});
		ui.addWindowListener(
				new WindowAdapter() {
					@Override
					public void windowClosed(WindowEvent event) {
						stopRefreshTimer();
					}
				});
	}

	private void connectToServer() {
		ClientWindow.Login settings = ui.values();
		ui.action(
				ui.login,
				() -> {
					controller.connect(settings.host(), settings.port());
					SwingUtilities.invokeLater(() -> openWorkspace(settings));
				});
	}

	private void openWorkspace(ClientWindow.Login settings) {
		ui.open(
				workspace(),
				"Người dùng",
				settings.host() + ":" + settings.port(),
				localIp(),
				this::stopRefreshTimer);
		startRefreshTimer();
		refresh();
	}

	private JPanel workspace() {
		JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBackground(AntTheme.BG);
		root.add(
				DesktopUi.row(refreshButton, status, new JLabel("Tự làm mới mỗi 2 giây")),
				BorderLayout.NORTH);
		root.add(new JScrollPane(table), BorderLayout.CENTER);
		root.add(createEditor(), BorderLayout.SOUTH);
		return root;
	}

	private JPanel createEditor() {
		JPanel editor = DesktopUi.card("Thêm / cập nhật bản ghi");
		String[] labels = {"Key", "Value"};
		JComponent[] inputs = {key, new JScrollPane(value)};
		editor.add(DesktopUi.form(labels, inputs), BorderLayout.CENTER);
		editor.add(DesktopUi.row(saveButton, deleteButton), BorderLayout.SOUTH);
		return editor;
	}

	private void saveEntry() {
		String entryKey = key.getText();
		String entryValue = value.getText();
		ui.action(
				saveButton,
				() -> {
					controller.put(entryKey, entryValue);
					refresh();
				});
	}

	private void deleteEntry() {
		String entryKey = key.getText();
		ui.action(
				deleteButton,
				() -> {
					controller.delete(entryKey);
					refresh();
				});
	}

	private void selectEntry() {
		int row = table.getSelectedRow();
		if (row >= 0) {
			key.setText(String.valueOf(table.getValueAt(row, 0)));
			value.setText(String.valueOf(table.getValueAt(row, 1)));
		}
	}

	private void startRefreshTimer() {
		stopRefreshTimer();
		timer = new Timer(2000, event -> refresh());
		timer.start();
	}

	private void stopRefreshTimer() {
		if (timer != null) {
			timer.stop();
		}
	}

	private void refresh() {
		// Không gửi thêm yêu cầu nếu lần tải trước vẫn chưa xong.
		if (!refreshing.compareAndSet(false, true)) {
			return;
		}
		Thread.startVirtualThread(this::loadSnapshot);
	}

	private void loadSnapshot() {
		try {
			Map<String, Store.Value> snapshot = controller.snapshot();
			String serverInfo = controller.info();
			SwingUtilities.invokeLater(() -> showSnapshot(snapshot, serverInfo));
		} catch (Exception exception) {
			SwingUtilities.invokeLater(() -> showConnectionError(exception));
		} finally {
			refreshing.set(false);
		}
	}

	private void showSnapshot(Map<String, Store.Value> snapshot, String serverInfo) {
		// TreeMap sắp xếp theo key. Vòng for thêm từng dòng, không dùng chuỗi stream.
		Map<String, Store.Value> sortedEntries = new TreeMap<>(snapshot);
		model.setRowCount(0);
		for (Map.Entry<String, Store.Value> entry : sortedEntries.entrySet()) {
			Store.Value entryValue = entry.getValue();
			model.addRow(
					new Object[] {
						entry.getKey(),
						entryValue.text(),
						entryValue.version(),
						entryValue.origin(),
						entryValue.deleted()
					});
		}
		status.setText(serverInfo.split("—", 2)[0].trim() + " · đã kết nối");
		status.setToolTipText(serverInfo);
		status.setForeground(AntDesignTheme.SUCCESS);
		status.setBackground(AntDesignTheme.SUCCESS_BG);
		status.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.SUCCESS_BORDER),
						BorderFactory.createEmptyBorder(2, 8, 2, 8)));
	}

	private void showConnectionError(Exception exception) {
		status.setText("Mất kết nối: " + exception.getMessage());
		status.setForeground(AntDesignTheme.ERROR);
	}

	private String localIp() {
		try (DatagramSocket probe = new DatagramSocket()) {
			probe.connect(
					InetAddress.getByName(DesktopUi.value(ui.host)),
					Integer.parseInt(DesktopUi.value(ui.port)));
			return probe.getLocalAddress().getHostAddress();
		} catch (Exception exception) {
			return "local";
		}
	}

	public static void main(String[] arguments) {
		DesktopUi.init();
		SwingUtilities.invokeLater(() -> new ClientUI(arguments));
	}
}
