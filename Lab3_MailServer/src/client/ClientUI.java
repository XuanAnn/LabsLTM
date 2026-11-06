package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.awt.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class ClientUI {
	private final ClientWindow ui = new ClientWindow("Hộp thư", 2525, 2110, true);
	private final ClientController controller = new ClientController();
	private final DefaultTableModel model = DesktopUi.model("Người gửi", "Tiêu đề", "Ngày gửi");
	private final JTable table = DesktopUi.table(model);
	private final JTextArea body = new JTextArea();
	private final JLabel folderTitle = AntTheme.label("Thư đến", 18, true, AntTheme.TEXT);
	private final JLabel status = AntTheme.label(" ", 12, false, AntTheme.MUTED);
	private final JButton composeButton = DesktopUi.button("+ Soạn thư", true);
	private final JButton gmailButton = DesktopUi.button("Soạn bằng Gmail", false);
	private final JButton inboxButton = DesktopUi.button("Thư đến", false);
	private final JButton sentButton = DesktopUi.button("Đã gửi", false);
	private final JButton profileButton = DesktopUi.button("Tài khoản", false);
	private final JButton settingsButton = DesktopUi.button("Cài đặt gửi thư", false);
	private final JButton refreshButton = DesktopUi.button("Làm mới", false);
	private final JButton deleteButton = DesktopUi.button("Xóa thư", false);
	private final AtomicLong requests = new AtomicLong();
	private List<ClientController.Mail> rows = new ArrayList<>();
	private volatile String folder = "INBOX";

	private ClientUI() {
		loadSenderAddress();
		configureMessageArea();
		registerEvents();
		ui.setVisible(true);
	}

	private void loadSenderAddress() {
		try {
			GmailSettings settings = GmailSettings.load();
			ui.email.setText(settings.account());
		} catch (Exception exception) {
			// Chưa có cấu hình gửi Gmail thì người dùng vẫn đăng nhập local bình thường.
		}
	}

	private void configureMessageArea() {
		body.setEditable(false);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setFont(AntDesignTheme.FONT_REGULAR);
		body.setMargin(new Insets(18, 18, 18, 18));
	}

	private void registerEvents() {
		ui.login.addActionListener(event -> login());
		ui.register.addActionListener(event -> registerAccount());
		composeButton.addActionListener(event -> compose(false));
		gmailButton.addActionListener(event -> compose(true));
		inboxButton.addActionListener(event -> selectFolder("INBOX"));
		sentButton.addActionListener(event -> selectFolder("SENT"));
		refreshButton.addActionListener(event -> refresh());
		deleteButton.addActionListener(event -> deleteSelectedMail());
		profileButton.addActionListener(event -> profile());
		settingsButton.addActionListener(event -> settings());
		table.getSelectionModel()
				.addListSelectionListener(
						event -> {
							if (!event.getValueIsAdjusting()) {
								showSelectedMail();
							}
						});
	}

	private void login() {
		ClientWindow.Login settings = ui.values();
		ui.action(
				ui.login,
				() -> {
					controller.login(settings);
					SwingUtilities.invokeLater(() -> openWorkspace(settings));
				});
	}

	private void openWorkspace(ClientWindow.Login settings) {
		ui.email.setText(controller.email());
		String account = settings.user() + " <" + controller.email() + ">";
		String endpoint = settings.host() + ":" + settings.port();
		ui.open(workspace(), account, endpoint, localIp(), requests::incrementAndGet);
		refresh();
	}

	private void registerAccount() {
		ClientWindow.Login settings = ui.values();
		String emailAddress = ui.email.getText().trim();
		ui.action(
				ui.register,
				() -> {
					if (emailAddress.isBlank()) {
						throw new Exception("Nhập email cho tài khoản mới");
					}
					controller.register(settings, emailAddress);
					ui.notice("Đăng ký thành công. Bạn có thể đăng nhập.");
				});
	}

	private JPanel workspace() {
		folder = "INBOX";
		JPanel root = new JPanel(new BorderLayout(16, 12));
		root.setBackground(AntTheme.BG);
		root.add(createSidebar(), BorderLayout.WEST);
		root.add(createMailboxPanel(), BorderLayout.CENTER);
		return root;
	}

	private JPanel createSidebar() {
		JPanel sidebar = DesktopUi.card("Hộp thư");
		sidebar.setPreferredSize(new Dimension(175, 500));
		JPanel navigation = new JPanel(new GridLayout(0, 1, 0, 10));
		navigation.add(composeButton);
		navigation.add(gmailButton);
		navigation.add(inboxButton);
		navigation.add(sentButton);
		navigation.add(profileButton);
		navigation.add(settingsButton);
		sidebar.add(navigation, BorderLayout.NORTH);
		return sidebar;
	}

	private JPanel createMailboxPanel() {
		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setOpaque(false);
		JPanel tools = new JPanel(new BorderLayout());
		tools.add(folderTitle, BorderLayout.WEST);
		tools.add(DesktopUi.row(refreshButton, deleteButton), BorderLayout.EAST);
		content.add(tools, BorderLayout.NORTH);
		JSplitPane split =
				new JSplitPane(
						JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(body));
		split.setResizeWeight(0.42);
		split.setDividerLocation(235);
		content.add(split, BorderLayout.CENTER);
		content.add(status, BorderLayout.SOUTH);
		return content;
	}

	private void showSelectedMail() {
		int selectedRow = table.getSelectedRow();
		if (selectedRow < 0) {
			return;
		}
		int index = table.convertRowIndexToModel(selectedRow);
		if (index >= rows.size()) {
			return;
		}
		ClientController.Mail mail = rows.get(index);
		String recipientIp = mail.recipientIp();
		if (recipientIp.isBlank()) {
			recipientIp = "Chưa kết nối";
		}
		StringBuilder text = new StringBuilder();
		text.append("Từ: ").append(mail.from());
		text.append("\nTới: ").append(mail.to());
		text.append("\nIP gửi: ").append(mail.senderIp());
		text.append("\nIP nhận: ").append(recipientIp);
		text.append("\nTiêu đề: ").append(mail.subject());
		text.append("\nNgày: ").append(mail.date());
		text.append("\n\n").append(mail.body());
		body.setText(text.toString());
		body.setCaretPosition(0);
	}

	private void selectFolder(String selectedFolder) {
		folder = selectedFolder;
		if (selectedFolder.equals("SENT")) {
			folderTitle.setText("Đã gửi");
		} else {
			folderTitle.setText("Thư đến");
		}
		body.setText("");
		refresh();
	}

	private void refresh() {
		String selectedFolder = folder;
		long requestNumber = requests.incrementAndGet();
		SwingUtilities.invokeLater(() -> status.setText("Đang tải thư…"));
		ui.action(null, () -> loadMailbox(selectedFolder, requestNumber));
	}

	private void loadMailbox(String selectedFolder, long requestNumber) throws Exception {
		List<ClientController.Mail> result = controller.messages(selectedFolder);
		SwingUtilities.invokeLater(() -> showMailbox(result, selectedFolder, requestNumber));
	}

	private void showMailbox(
			List<ClientController.Mail> result, String selectedFolder, long requestNumber) {
		// Người dùng có thể đổi thư mục trong khi socket đang tải thư.
		// Bỏ kết quả cũ để nó không ghi đè lên thư mục vừa chọn.
		if (requestNumber != requests.get() || !selectedFolder.equals(folder)) {
			return;
		}
		boolean sentFolder = selectedFolder.equals("SENT");
		String personColumn = "Người gửi";
		if (sentFolder) {
			personColumn = "Người nhận";
		}
		rows = result;
		model.setRowCount(0);
		model.setColumnIdentifiers(new String[] {personColumn, "Tiêu đề", "Ngày gửi"});
		for (ClientController.Mail mail : result) {
			String person = mail.from();
			if (sentFolder) {
				person = mail.to();
			}
			model.addRow(new Object[] {person, mail.subject(), mail.date()});
		}
		status.setText(result.size() + " thư");
		if (!result.isEmpty()) {
			table.setRowSelectionInterval(0, 0);
		} else if (sentFolder) {
			body.setText("Bạn chưa có thư đã gửi");
		} else {
			body.setText("Hộp thư trống");
		}
	}

	private void deleteSelectedMail() {
		int selectedRow = table.getSelectedRow();
		if (selectedRow < 0) {
			status.setText("Chọn thư cần xóa");
			return;
		}
		ClientController.Mail mail = rows.get(table.convertRowIndexToModel(selectedRow));
		String folderName = "Thư đến";
		if (mail.folder().equals("SENT")) {
			folderName = "Đã gửi";
		}
		String question = "Xóa thư “" + mail.subject() + "” khỏi " + folderName + "?";
		int answer =
				JOptionPane.showConfirmDialog(ui, question, "Xóa thư", JOptionPane.YES_NO_OPTION);
		if (answer != JOptionPane.YES_OPTION) {
			return;
		}
		ui.action(
				deleteButton,
				() -> {
					controller.delete(mail.folder(), mail.uid());
					SwingUtilities.invokeLater(() -> status.setText("Đã xóa thư"));
					refresh();
				});
	}

	private void compose(boolean useGmail) {
		try {
			if (useGmail && !GmailSettings.load().configured()) {
				settings();
				if (!GmailSettings.load().configured()) {
					return;
				}
			}
			ComposeDialog dialog = new ComposeDialog(ui, controller, useGmail, this::afterSend);
			dialog.setVisible(true);
		} catch (Exception exception) {
			JOptionPane.showMessageDialog(
					ui, exception.getMessage(), "Soạn thư", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void afterSend() {
		refresh();
		JOptionPane.showMessageDialog(ui, "Đã gửi thư thành công");
	}

	private void profile() {
		JTextField name = DesktopUi.field(ui.user.getText(), 28);
		JTextField email = DesktopUi.field(controller.email(), 28);
		name.setEditable(false);
		JPanel form =
				DesktopUi.form(
						new String[] {"Tên tài khoản", "Email gửi và nhận thư"},
						new JComponent[] {name, email});
		if (JOptionPane.showConfirmDialog(ui, form, "Tài khoản", JOptionPane.OK_CANCEL_OPTION)
				== JOptionPane.OK_OPTION) {
			String address = email.getText();
			String username = name.getText();
			ui.action(null, () -> updateProfile(username, address));
		}
	}

	private void updateProfile(String username, String address) throws Exception {
		controller.updateEmail(address);
		SwingUtilities.invokeLater(() -> ui.email.setText(controller.email()));
		ui.setAccount(username + " <" + controller.email() + ">");
	}

	private void settings() {
		try {
			GmailSettings current = GmailSettings.load();
			JTextField account = DesktopUi.field(current.account(), 30);
			JTextField password = DesktopUi.field(current.password(), 30);
			JPanel form =
					DesktopUi.form(
							new String[] {"Địa chỉ Gmail gửi thư", "Mật khẩu ứng dụng"},
							new JComponent[] {account, password});
			if (JOptionPane.showConfirmDialog(
							ui, form, "Cài đặt gửi thư", JOptionPane.OK_CANCEL_OPTION)
					== JOptionPane.OK_OPTION) {
				GmailSettings updated = new GmailSettings(account.getText(), password.getText());
				if (!updated.configured()) {
					throw new java.io.IOException("Hãy nhập địa chỉ Gmail và mật khẩu ứng dụng");
				}
				updated.save();
				status.setText("Đã lưu cài đặt gửi thư");
			}
		} catch (Exception exception) {
			JOptionPane.showMessageDialog(
					ui, exception.getMessage(), "Cài đặt gửi thư", JOptionPane.ERROR_MESSAGE);
		}
	}

	private String localIp() {
		try (DatagramSocket probe = new DatagramSocket()) {
			probe.connect(
					InetAddress.getByName(DesktopUi.value(ui.host)),
					Integer.parseInt(DesktopUi.value(ui.port)));
			return probe.getLocalAddress().getHostAddress();
		} catch (Exception exception) {
			return "Không xác định";
		}
	}

	public static void main(String[] arguments) {
		DesktopUi.init();
		SwingUtilities.invokeLater(ClientUI::new);
	}
}
