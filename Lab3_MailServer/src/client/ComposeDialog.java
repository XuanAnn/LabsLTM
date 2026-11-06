package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;

/** Compose one local SMTP transaction for one, selected, or all local accounts. */
public final class ComposeDialog extends JDialog {
	public enum Mode {
		DIRECT("Gửi riêng"),
		GROUP("Gửi nhóm"),
		ALL("Gửi tất cả");
		private final String label;

		Mode(String label) {
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}

	private final ClientController controller;
	private final boolean gmail;
	private final Runnable success;
	private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
	private final JTextField to = DesktopUi.field("", 40), subject = DesktopUi.field("", 40);
	private final JTextArea body = new JTextArea(14, 60);
	private final JButton picker = DesktopUi.button("Chọn người nhận", false),
			send = DesktopUi.button("Gửi thư", true);
	private final JCheckBox includeSelf = new JCheckBox("Gửi cả cho tôi");
	private final JLabel hint = AntTheme.label(" ", 12, false, AntTheme.MUTED);
	private List<ClientController.Contact> contacts = List.of();
	private boolean loading, wasAll;
	private String previousRecipients = "";

	public ComposeDialog(Frame owner, ClientController controller, boolean gmail, Runnable success)
			throws Exception {
		super(owner, gmail ? "Soạn thư bằng Gmail" : "Soạn thư", false);
		this.controller = controller;
		this.gmail = gmail;
		this.success = success;
		mode.setName("send-mode");
		to.setName("recipients");
		subject.setName("subject");
		body.setName("body");
		send.setName("send-message");
		picker.setName("choose-recipients");
		includeSelf.setName("include-self");
		hint.setName("recipient-hint");
		String from =
				gmail
						? controller.username() + " <" + GmailSettings.load().account() + ">"
						: controller.displaySender();
		JTextField sender = DesktopUi.field(from, 40);
		sender.setEditable(false);
		JPanel recipients = new JPanel(new BorderLayout(8, 0));
		recipients.add(to, BorderLayout.CENTER);
		if (!gmail) {
			recipients.add(picker, BorderLayout.EAST);
		}
		JPanel form =
				DesktopUi.form(
						gmail
								? new String[] {"Từ", "Người nhận", "Tiêu đề"}
								: new String[] {"Từ", "Cách gửi", "Người nhận", "Tiêu đề"},
						gmail
								? new JComponent[] {sender, recipients, subject}
								: new JComponent[] {sender, mode, recipients, subject});
		JPanel top = new JPanel(new BorderLayout(0, 8));
		top.add(form, BorderLayout.CENTER);
		top.add(DesktopUi.row(includeSelf, hint), BorderLayout.SOUTH);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setFont(AntDesignTheme.FONT_REGULAR);
		body.setMargin(new Insets(12, 12, 12, 12));
		JPanel card = DesktopUi.card(null);
		card.add(top, BorderLayout.NORTH);
		card.add(new JScrollPane(body), BorderLayout.CENTER);
		JButton cancel = DesktopUi.button("Hủy", false);
		card.add(DesktopUi.row(cancel, send), BorderLayout.SOUTH);
		add(card);
		pack();
		setMinimumSize(new Dimension(790, 590));
		setLocationRelativeTo(owner);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		cancel.addActionListener(e -> dispose());
		mode.addActionListener(e -> updateMode());
		includeSelf.addActionListener(e -> updateMode());
		picker.addActionListener(
				e -> {
					if (mode.getSelectedItem() != Mode.ALL) {
						loadContacts(true);
					}
				});
		send.addActionListener(e -> submit());
		updateMode();
		if (!gmail) {
			loadContacts(false);
		}
	}

	private void updateMode() {
		boolean all = !gmail && mode.getSelectedItem() == Mode.ALL;
		if (all && !wasAll) {
			previousRecipients = to.getText();
			to.setText("Tất cả tài khoản trên máy chủ local");
		} else if (!all && wasAll) {
			to.setText(previousRecipients);
		}
		wasAll = all;
		to.setEditable(!all);
		includeSelf.setVisible(all);
		picker.setEnabled(!all && !loading);
		if (gmail) {
			hint.setText("Nhập email của người nhận");
		} else if (all) {
			long count = 0;
			String sender = safeUsername();
			for (ClientController.Contact contact : contacts) {
				if (includeSelf.isSelected() || !contact.username().equalsIgnoreCase(sender)) {
					count++;
				}
			}
			hint.setText(
					(contacts.isEmpty() ? "" : count + " tài khoản · ")
							+ "Bao gồm người đang ngoại tuyến");
		} else {
			hint.setText(
					mode.getSelectedItem() == Mode.GROUP
							? "Chọn nhiều tài khoản hoặc nhập các email cách nhau bằng dấu phẩy"
							: "Chọn một tài khoản hoặc nhập email người nhận");
		}
	}

	private String safeUsername() {
		try {
			return controller.username();
		} catch (Exception exception) {
			return "";
		}
	}

	private void loadContacts(boolean open) {
		loading = true;
		picker.setEnabled(false);
		picker.setText("Đang tải…");
		Thread.startVirtualThread(
				() -> {
					try {
						List<ClientController.Contact> result = controller.contacts();
						SwingUtilities.invokeLater(
								() -> {
									contacts = result;
									loading = false;
									picker.setText("Chọn người nhận");
									updateMode();
									if (open && isDisplayable()) {
										chooseRecipients();
									}
								});
					} catch (Exception exception) {
						SwingUtilities.invokeLater(
								() -> {
									loading = false;
									picker.setText("Chọn người nhận");
									updateMode();
									if (isDisplayable()) {
										JOptionPane.showMessageDialog(
												this,
												exception.getMessage(),
												"Danh sách người nhận",
												JOptionPane.ERROR_MESSAGE);
									}
								});
					}
				});
	}

	private void chooseRecipients() {
		boolean group = mode.getSelectedItem() == Mode.GROUP;
		JDialog dialog =
				new JDialog(this, "Chọn người nhận từ máy chủ", Dialog.ModalityType.DOCUMENT_MODAL);
		DefaultTableModel model =
				new DefaultTableModel(new String[] {"Chọn", "Tài khoản", "Email"}, 0) {
					public Class<?> getColumnClass(int column) {
						return column == 0 ? Boolean.class : String.class;
					}

					public boolean isCellEditable(int row, int column) {
						return column == 0;
					}
				};
		Set<String> checked = new HashSet<>(ClientController.recipientAddresses(to.getText()));
		for (ClientController.Contact contact : contacts) {
			model.addRow(
					new Object[] {
						checked.contains(contact.email().toLowerCase(Locale.ROOT)),
						contact.username(),
						contact.email()
					});
		}
		JTable table = DesktopUi.table(model);
		table.setName("recipient-list");
		table.getColumnModel().getColumn(0).setMaxWidth(55);
		boolean[] updating = {false};
		model.addTableModelListener(
				e -> {
					if (!group
							&& !updating[0]
							&& e.getType() == TableModelEvent.UPDATE
							&& e.getColumn() == 0
							&& Boolean.TRUE.equals(model.getValueAt(e.getFirstRow(), 0))) {
						updating[0] = true;
						for (int count = 0; count < model.getRowCount(); count++) {
							if (count != e.getFirstRow()) {
								model.setValueAt(false, count, 0);
							}
						}
						updating[0] = false;
					}
				});
		JButton all = DesktopUi.button("Chọn tất cả", false),
				none = DesktopUi.button("Bỏ chọn", false),
				confirm = DesktopUi.button("Xác nhận", true),
				cancel = DesktopUi.button("Hủy", false);
		all.setEnabled(group);
		all.addActionListener(
				e -> {
					for (int count2 = 0; count2 < model.getRowCount(); count2++) {
						model.setValueAt(true, count2, 0);
					}
				});
		none.addActionListener(
				e -> {
					for (int count3 = 0; count3 < model.getRowCount(); count3++) {
						model.setValueAt(false, count3, 0);
					}
				});
		cancel.addActionListener(e -> dialog.dispose());
		confirm.addActionListener(
				e -> {
					if (table.isEditing()) {
						table.getCellEditor().stopCellEditing();
					}
					List<ClientController.Contact> selected = new ArrayList<>();
					for (int count4 = 0; count4 < model.getRowCount(); count4++) {
						if (Boolean.TRUE.equals(model.getValueAt(count4, 0))) {
							selected.add(contacts.get(count4));
						}
					}
					if ((group ? selected.size() < 2 : selected.size() != 1)) {
						JOptionPane.showMessageDialog(
								dialog,
								group ? "Chọn ít nhất hai người nhận" : "Chọn một người nhận");
						return;
					}
					List<String> recipientNames = new ArrayList<>();
					for (ClientController.Contact contact : selected) {
						recipientNames.add(contact.display());
					}
					to.setText(String.join(", ", recipientNames));
					dialog.dispose();
				});
		JPanel panel =
				DesktopUi.card(
						group
								? "Chọn những tài khoản nhận thư nhóm"
								: "Chọn tài khoản nhận thư riêng");
		panel.add(DesktopUi.row(all, none), BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		panel.add(DesktopUi.row(cancel, confirm), BorderLayout.SOUTH);
		dialog.add(panel);
		dialog.setSize(650, 480);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void submit() {
		Mode selected = (Mode) mode.getSelectedItem();
		String input = to.getText(), title = subject.getText(), content = body.getText();
		boolean self = includeSelf.isSelected();
		DesktopUi.work(
				this,
				send,
				() -> {
					List<String> addresses =
							selected == Mode.ALL && !gmail
									? List.of()
									: ClientController.recipientAddresses(input);
					if (gmail || selected == Mode.DIRECT) {
						if (addresses.size() != 1) {
							throw new Exception("Chế độ gửi riêng cần đúng một người nhận");
						}
						if (gmail) {
							controller.sendGmail(addresses.getFirst(), title, content);
						} else {
							controller.send(addresses.getFirst(), title, content);
						}
					} else if (selected == Mode.GROUP) {
						if (addresses.size() < 2) {
							throw new Exception("Chọn ít nhất hai người nhận để gửi nhóm");
						}
						controller.sendGroup(addresses, title, content);
					} else {
						controller.sendBroadcast(self, title, content);
					}
					SwingUtilities.invokeLater(
							() -> {
								dispose();
								success.run();
							});
				});
	}
}
