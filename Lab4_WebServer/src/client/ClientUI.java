package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.awt.*;
import java.awt.event.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Mỗi thao tác giao diện có một hàm riêng để dễ theo dõi và đặt breakpoint. */
public final class ClientUI {

	private final ClientWindow ui = new ClientWindow("Trình duyệt", 8080, 0, false);
	private final ClientController controller = new ClientController();
	private final JTextField url = DesktopUi.field("http://localhost:8080/", 36);
	private final JComboBox<String> method = new JComboBox<>(new String[] {"GET", "HEAD", "POST"});
	private final JTextArea requestBody = new JTextArea(4, 45);
	private final JTextArea responseBody = new JTextArea();
	private final JTextArea headers = new JTextArea();
	private final JLabel responseState = DesktopUi.tag("Sẵn sàng", true);
	private final DefaultTableModel history =
			DesktopUi.model("Thời gian", "Phương thức", "Địa chỉ", "Trạng thái");
	private final JTable historyTable = DesktopUi.table(history);
	private final JButton sendButton = DesktopUi.button("Gửi request", true);
	private final JButton downloadButton = DesktopUi.button("Tải file", false);
	private final JButton viewButton = DesktopUi.button("Xem trong trình duyệt", false);
	private final JButton refreshButton = DesktopUi.button("Làm mới lịch sử", false);
	private JTabbedPane tabs;
	private EmbeddedBrowser browser;

	private ClientUI() {
		configureTextAreas();
		registerEvents();
		Rectangle bounds =
				GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		ui.setSize(Math.min(1440, bounds.width), Math.min(960, bounds.height));
		ui.setLocation(bounds.x, bounds.y);
		ui.setVisible(true);
	}

	private void configureTextAreas() {
		responseBody.setEditable(false);
		responseBody.setFont(new Font("Consolas", Font.PLAIN, 13));
		headers.setEditable(false);
		headers.setFont(new Font("Consolas", Font.PLAIN, 13));
	}

	private void registerEvents() {
		ui.login.addActionListener(event -> connectToServer());
		sendButton.addActionListener(event -> sendRequest());
		url.addActionListener(event -> sendRequest());
		downloadButton.addActionListener(event -> chooseDownloadFile());
		viewButton.addActionListener(event -> openInBrowser());
		refreshButton.addActionListener(event -> refreshHistory());
		historyTable
				.getSelectionModel()
				.addListSelectionListener(
						event -> {
							if (!event.getValueIsAdjusting()) {
								selectHistoryAddress();
							}
						});
		historyTable.addMouseListener(
				new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent event) {
						if (event.getClickCount() == 2 && historyTable.getSelectedRow() >= 0) {
							openInBrowser();
						}
					}
				});
		ui.addWindowListener(
				new WindowAdapter() {
					@Override
					public void windowClosed(WindowEvent event) {
						closeBrowser();
						BrowserRuntime.shutdown();
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
		String endpoint = settings.host() + ":" + settings.port();
		url.setText("http://" + endpoint + "/");
		ui.open(workspace(), "Trình duyệt", endpoint, localIp(), this::closeBrowser);
		refreshHistory();
	}

	private JPanel workspace() {
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(AntTheme.BG);
		tabs = new JTabbedPane();
		browser = new EmbeddedBrowser(url.getText(), this::recordBrowserVisit, controller);
		tabs.addTab("Trình duyệt", browser);
		tabs.addTab("Yêu cầu", createRequestPanel());
		tabs.addTab("Lịch sử", createHistoryPanel());
		root.add(tabs, BorderLayout.CENTER);
		return root;
	}

	private JPanel createRequestPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 12));
		panel.setOpaque(false);
		JPanel toolbar = DesktopUi.card(null);
		toolbar.add(
				DesktopUi.row(method, url, sendButton, downloadButton, viewButton, responseState));
		panel.add(toolbar, BorderLayout.NORTH);

		JTabbedPane resultTabs = new JTabbedPane();
		resultTabs.addTab("Nội dung", new JScrollPane(responseBody));
		resultTabs.addTab("Thông tin phản hồi", new JScrollPane(headers));
		panel.add(resultTabs, BorderLayout.CENTER);

		JPanel bodyPanel = DesktopUi.card("Nội dung gửi");
		bodyPanel.add(new JScrollPane(requestBody), BorderLayout.CENTER);
		bodyPanel.setPreferredSize(new Dimension(800, 150));
		panel.add(bodyPanel, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel createHistoryPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(refreshButton, BorderLayout.NORTH);
		panel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
		return panel;
	}

	private void sendRequest() {
		String address = url.getText();
		String selectedMethod = String.valueOf(method.getSelectedItem());
		String content = requestBody.getText();
		ui.action(sendButton, () -> loadResponse(address, selectedMethod, content));
	}

	private void loadResponse(String address, String selectedMethod, String content)
			throws Exception {
		// Gửi socket trên luồng nền, sau đó chuyển về EDT để thay đổi component Swing.
		WebClient.Response response = controller.request(address, selectedMethod, content);
		SwingUtilities.invokeLater(() -> showResponse(response));
		refreshHistory();
	}

	private void showResponse(WebClient.Response response) {
		responseState.setText(response.status());
		StringBuilder headerText = new StringBuilder(response.status());
		for (Map.Entry<String, String> header : response.headers().entrySet()) {
			headerText.append("\n");
			headerText.append(header.getKey());
			headerText.append(": ");
			headerText.append(header.getValue());
		}
		headers.setText(headerText.toString());

		String contentType = response.headers().getOrDefault("content-type", "");
		boolean isText =
				contentType.startsWith("text/")
						|| contentType.contains("json")
						|| contentType.contains("xml");
		if (isText) {
			responseBody.setText(new String(response.body(), StandardCharsets.UTF_8));
		} else {
			responseBody.setText(
					"Đã nhận "
							+ response.body().length
							+ " byte. Chọn Tải file để lưu hoặc Xem trong trình duyệt.");
		}
		responseBody.setCaretPosition(0);
	}

	private void chooseDownloadFile() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showSaveDialog(ui) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		String address = url.getText();
		Path destination = chooser.getSelectedFile().toPath();
		ui.action(downloadButton, () -> downloadFile(address, destination));
	}

	private void downloadFile(String address, Path destination) throws Exception {
		controller.download(address, destination);
		SwingUtilities.invokeLater(
				() -> responseState.setText("Đã tải: " + destination.getFileName()));
		refreshHistory();
	}

	private void openInBrowser() {
		browser.navigate(url.getText());
		tabs.setSelectedIndex(0);
	}

	private void selectHistoryAddress() {
		int row = historyTable.getSelectedRow();
		if (row >= 0) {
			url.setText(String.valueOf(historyTable.getValueAt(row, 2)));
		}
	}

	private void recordBrowserVisit(String address, int status) {
		ui.action(
				null,
				() -> {
					controller.recordVisit(address, status);
					refreshHistory();
				});
	}

	private void refreshHistory() {
		ui.action(null, () -> DesktopUi.rows(history, controller.history()));
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

	private void closeBrowser() {
		if (browser != null) {
			browser.close();
			browser = null;
		}
		controller.disconnect();
	}

	public static void main(String[] args) {
		DesktopUi.init();
		SwingUtilities.invokeLater(ClientUI::new);
	}
}
