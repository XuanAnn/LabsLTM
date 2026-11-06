package server;

import common.AntDesignTheme;
import common.ChatRecord;
import common.GroupInfo;
import common.Message;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class ServerUI extends JFrame implements ServerController.ServerEventListener {
	private ServerController controller;

	// Header Controls
	private JTextField txtPort;
	private JButton btnStartStop;
	private JLabel lblStatusBadge;

	// Stats Cards
	private JLabel lblOnlineStat;
	private JLabel lblAccountStat;
	private JLabel lblGroupStat;
	private JLabel lblMsgStat;

	// User Table
	private DefaultTableModel userTableModel;
	private JTable userTable;
	private JButton btnAddUser;
	private JButton btnDeleteUser;
	private JButton btnKickUser;
	private JButton btnResetPassword;

	// Group Table
	private DefaultTableModel groupTableModel;
	private JTable groupTable;
	private JButton btnAddGroup;
	private JButton btnDeleteGroup;

	// Message Monitor
	private DefaultTableModel messageTableModel;
	private JTable messageTable;
	private JTextField txtBroadcastMsg;
	private JButton btnSendBroadcast;

	// Logs
	private JTextArea txtLogs;

	public ServerUI() {
		super("Quản lý trò chuyện");
		AntDesignTheme.setup();
		controller = new ServerController(this);
		initComponents();
		for (ChatRecord record : controller.getChatHistory()) {
			onMessageLogged(record);
		}
		controller.startServer(8888);
	}

	private void initComponents() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1100, 750);
		setMinimumSize(new Dimension(900, 600));
		setLocationRelativeTo(null);
		getContentPane().setBackground(AntDesignTheme.BG_LAYOUT);

		// Main Layout: Header + Stats + Tabbed Content
		JPanel rootPanel = new JPanel(new BorderLayout(0, 12));
		rootPanel.setBackground(AntDesignTheme.BG_LAYOUT);
		rootPanel.setBorder(new EmptyBorder(12, 16, 16, 16));

		// 1. Ant Design Header Bar
		rootPanel.add(createHeaderPanel(), BorderLayout.NORTH);

		// 2. Center: Stats Cards + Tabs
		JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
		centerPanel.setOpaque(false);
		centerPanel.add(createStatsPanel(), BorderLayout.NORTH);

		// Ant Tabs
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setFont(AntDesignTheme.FONT_BOLD);
		tabbedPane.setBackground(Color.WHITE);

		tabbedPane.addTab("👤 Quản lý Tài khoản & Trạng thái", createUsersPanel());
		tabbedPane.addTab("👥 Nhóm trò chuyện", createGroupsPanel());
		tabbedPane.addTab("💬 Giám sát Tin nhắn & Broadcast", createMessagesPanel());
		tabbedPane.addTab("📋 Nhật ký Hoạt động (Logs)", createLogsPanel());

		centerPanel.add(tabbedPane, BorderLayout.CENTER);
		rootPanel.add(centerPanel, BorderLayout.CENTER);

		add(rootPanel);
	}

	private JPanel createHeaderPanel() {
		JPanel header = new JPanel(new BorderLayout(15, 0));
		header.setBackground(Color.WHITE);
		header.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(12, 20, 12, 20)));

		// Left Brand Logo & Title
		JPanel leftBrand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		leftBrand.setOpaque(false);

		JLabel logoIcon = new JLabel("⚡");
		logoIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
		logoIcon.setForeground(AntDesignTheme.PRIMARY);

		JPanel titles = new JPanel(new GridLayout(2, 1));
		titles.setOpaque(false);
		JLabel mainTitle = new JLabel("Chat Server Admin Dashboard");
		mainTitle.setFont(AntDesignTheme.FONT_TITLE_LARGE);
		mainTitle.setForeground(AntDesignTheme.TEXT_TITLE);

		JLabel subTitle = new JLabel("Quản lý tài khoản, nhóm và hoạt động");
		subTitle.setFont(AntDesignTheme.FONT_SMALL);
		subTitle.setForeground(AntDesignTheme.TEXT_MUTED);

		titles.add(mainTitle);
		titles.add(subTitle);

		leftBrand.add(logoIcon);
		leftBrand.add(titles);
		header.add(leftBrand, BorderLayout.WEST);

		// Right Port & Control
		JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
		rightControls.setOpaque(false);

		JLabel lblPort = new JLabel("Port:");
		lblPort.setFont(AntDesignTheme.FONT_BOLD);
		lblPort.setForeground(AntDesignTheme.TEXT_BODY);

		txtPort = new JTextField("8888", 5);
		txtPort.setFont(AntDesignTheme.FONT_REGULAR);
		txtPort.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(5, 8, 5, 8)));

		btnStartStop = AntDesignTheme.createDangerButton("Dừng Server");
		btnStartStop.addActionListener(e -> toggleServer());

		lblStatusBadge =
				AntDesignTheme.createTag(
						"● ĐANG CHẠY : 8888",
						AntDesignTheme.SUCCESS_BG,
						AntDesignTheme.SUCCESS,
						AntDesignTheme.SUCCESS_BORDER);

		rightControls.add(lblPort);
		rightControls.add(txtPort);
		rightControls.add(btnStartStop);
		rightControls.add(lblStatusBadge);

		header.add(rightControls, BorderLayout.EAST);
		return header;
	}

	private JPanel createStatsPanel() {
		JPanel statsBar = new JPanel(new GridLayout(1, 4, 12, 0));
		statsBar.setOpaque(false);

		lblOnlineStat = new JLabel("0");
		lblAccountStat = new JLabel("0");
		lblGroupStat = new JLabel("0");
		lblMsgStat = new JLabel("0");

		statsBar.add(createStatCard("🟢 Đang Online", lblOnlineStat, AntDesignTheme.SUCCESS));
		statsBar.add(createStatCard("👤 Tổng Tài khoản", lblAccountStat, AntDesignTheme.PRIMARY));
		statsBar.add(
				createStatCard("👥 Nhóm trò chuyện", lblGroupStat, new Color(0x72, 0x2E, 0xD1)));
		statsBar.add(createStatCard("💬 Tổng Tin nhắn", lblMsgStat, AntDesignTheme.WARNING));

		return statsBar;
	}

	private JPanel createStatCard(String title, JLabel valueLabel, Color accentColor) {
		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.setBackground(Color.WHITE);
		card.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(12, 16, 12, 16)));

		JLabel lblTitle = new JLabel(title);
		lblTitle.setFont(AntDesignTheme.FONT_SMALL);
		lblTitle.setForeground(AntDesignTheme.TEXT_MUTED);

		valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
		valueLabel.setForeground(accentColor);

		card.add(lblTitle, BorderLayout.NORTH);
		card.add(valueLabel, BorderLayout.CENTER);
		return card;
	}

	private JPanel createUsersPanel() {
		JPanel card = AntDesignTheme.createCard("");

		String[] columns = {
			"Tài khoản",
			"Trạng thái",
			"IP Đang Kết Nối",
			"Thời Gian Vào (Login)",
			"Thời Gian Ra (Logout)",
			"Mật khẩu",
			"Ngày tạo (createdAt)"
		};
		userTableModel =
				new DefaultTableModel(columns, 0) {
					@Override
					public boolean isCellEditable(int row, int col) {
						return false;
					}
				};
		userTable = new JTable(userTableModel);
		userTable.setRowHeight(36);

		// Custom Status Tag Renderer (Ant Design Tag style)
		userTable
				.getColumnModel()
				.getColumn(1)
				.setCellRenderer(
						new DefaultTableCellRenderer() {
							@Override
							public Component getTableCellRendererComponent(
									JTable table,
									Object value,
									boolean isSelected,
									boolean hasFocus,
									int row,
									int column) {
								JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
								cell.setBackground(
										isSelected ? AntDesignTheme.PRIMARY_BG : Color.WHITE);
								if ("ONLINE".equals(value)) {
									JLabel tag =
											AntDesignTheme.createTag(
													"Online",
													AntDesignTheme.SUCCESS_BG,
													AntDesignTheme.SUCCESS,
													AntDesignTheme.SUCCESS_BORDER);
									cell.add(tag);
								} else {
									JLabel tag =
											AntDesignTheme.createTag(
													"Offline",
													AntDesignTheme.BG_LAYOUT,
													AntDesignTheme.TEXT_MUTED,
													AntDesignTheme.BORDER);
									cell.add(tag);
								}
								return cell;
							}
						});

		// Custom IP Tag Renderer
		userTable
				.getColumnModel()
				.getColumn(2)
				.setCellRenderer(
						new DefaultTableCellRenderer() {
							@Override
							public Component getTableCellRendererComponent(
									JTable table,
									Object value,
									boolean isSelected,
									boolean hasFocus,
									int row,
									int column) {
								JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
								cell.setBackground(
										isSelected ? AntDesignTheme.PRIMARY_BG : Color.WHITE);
								String ipText = value != null ? value.toString() : "-";
								if (!"-".equals(ipText)) {
									JLabel tag =
											AntDesignTheme.createTag(
													ipText,
													AntDesignTheme.PRIMARY_BG,
													AntDesignTheme.PRIMARY,
													AntDesignTheme.PRIMARY_HOVER);
									cell.add(tag);
								} else {
									JLabel lbl = new JLabel("-");
									lbl.setForeground(AntDesignTheme.TEXT_MUTED);
									cell.add(lbl);
								}
								return cell;
							}
						});

		JScrollPane scroll = new JScrollPane(userTable);
		scroll.setBorder(BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1));
		card.add(scroll, BorderLayout.CENTER);

		// Action Toolbar
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		actions.setOpaque(false);

		btnAddUser = AntDesignTheme.createPrimaryButton("+ Thêm tài khoản");
		btnResetPassword = AntDesignTheme.createDefaultButton("Đổi mật khẩu");
		btnKickUser = AntDesignTheme.createDefaultButton("Kick Offline");
		btnKickUser.setForeground(AntDesignTheme.WARNING);
		btnDeleteUser = AntDesignTheme.createDangerButton("Xoá tài khoản");

		btnAddUser.addActionListener(e -> showAddUserDialog());
		btnResetPassword.addActionListener(e -> showResetPasswordDialog());
		btnKickUser.addActionListener(e -> handleKickUser());
		btnDeleteUser.addActionListener(e -> handleDeleteUser());

		actions.add(btnAddUser);
		actions.add(btnResetPassword);
		actions.add(btnKickUser);
		actions.add(btnDeleteUser);

		card.add(actions, BorderLayout.SOUTH);
		return card;
	}

	private JPanel createGroupsPanel() {
		JPanel card = AntDesignTheme.createCard("");

		String[] columns = {
			"Mã Nhóm",
			"Tên Nhóm",
			"Địa chỉ nhóm",
			"Cổng nhóm",
			"Số thành viên",
			"Danh sách thành viên"
		};
		groupTableModel =
				new DefaultTableModel(columns, 0) {
					@Override
					public boolean isCellEditable(int row, int col) {
						return false;
					}
				};
		groupTable = new JTable(groupTableModel);
		groupTable.setRowHeight(36);

		// Highlight Multicast IP as Ant Design Tag
		groupTable
				.getColumnModel()
				.getColumn(2)
				.setCellRenderer(
						new DefaultTableCellRenderer() {
							@Override
							public Component getTableCellRendererComponent(
									JTable table,
									Object value,
									boolean isSelected,
									boolean hasFocus,
									int row,
									int column) {
								JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
								cell.setBackground(
										isSelected ? AntDesignTheme.PRIMARY_BG : Color.WHITE);
								JLabel tag =
										AntDesignTheme.createTag(
												String.valueOf(value),
												AntDesignTheme.PRIMARY_BG,
												AntDesignTheme.PRIMARY,
												AntDesignTheme.PRIMARY_HOVER);
								cell.add(tag);
								return cell;
							}
						});

		JScrollPane scroll = new JScrollPane(groupTable);
		scroll.setBorder(BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1));
		card.add(scroll, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		actions.setOpaque(false);

		btnAddGroup = AntDesignTheme.createPrimaryButton("+ Tạo Nhóm Mới");
		btnDeleteGroup = AntDesignTheme.createDangerButton("Giải tán Nhóm");

		btnAddGroup.addActionListener(e -> showAddGroupDialog());
		btnDeleteGroup.addActionListener(e -> handleDeleteGroup());

		actions.add(btnAddGroup);
		actions.add(btnDeleteGroup);

		card.add(actions, BorderLayout.SOUTH);
		return card;
	}

	private JPanel createMessagesPanel() {
		JPanel card = AntDesignTheme.createCard("");

		String[] columns = {"Thời gian", "Hình thức", "Người gửi", "Đích đến", "Nội dung tin nhắn"};
		messageTableModel =
				new DefaultTableModel(columns, 0) {
					@Override
					public boolean isCellEditable(int row, int col) {
						return false;
					}
				};
		messageTable = new JTable(messageTableModel);
		messageTable.setRowHeight(34);

		// Renderer for Type Tag
		messageTable
				.getColumnModel()
				.getColumn(1)
				.setCellRenderer(
						new DefaultTableCellRenderer() {
							@Override
							public Component getTableCellRendererComponent(
									JTable table,
									Object value,
									boolean isSelected,
									boolean hasFocus,
									int row,
									int column) {
								JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
								cell.setBackground(
										isSelected ? AntDesignTheme.PRIMARY_BG : Color.WHITE);
								if (String.valueOf(value).equals("DIRECT")) {
									JLabel tag =
											AntDesignTheme.createTag(
													"1 - 1",
													AntDesignTheme.PRIMARY_BG,
													AntDesignTheme.PRIMARY,
													AntDesignTheme.PRIMARY_HOVER);
									cell.add(tag);
								} else {
									JLabel tag =
											AntDesignTheme.createTag(
													"MULTICAST",
													AntDesignTheme.SUCCESS_BG,
													AntDesignTheme.SUCCESS,
													AntDesignTheme.SUCCESS_BORDER);
									cell.add(tag);
								}
								return cell;
							}
						});

		JScrollPane scroll = new JScrollPane(messageTable);
		scroll.setBorder(BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1));
		card.add(scroll, BorderLayout.CENTER);

		// Ant Design Alert / Broadcast Input
		JPanel broadcastBar = new JPanel(new BorderLayout(10, 0));
		broadcastBar.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createEmptyBorder(10, 0, 0, 0),
						BorderFactory.createCompoundBorder(
								BorderFactory.createLineBorder(
										AntDesignTheme.PRIMARY_HOVER, 1, true),
								new EmptyBorder(8, 12, 8, 12))));
		broadcastBar.setBackground(AntDesignTheme.PRIMARY_BG);

		JLabel lblBcastIcon = new JLabel("📢 Phát thông báo hệ thống:");
		lblBcastIcon.setFont(AntDesignTheme.FONT_BOLD);
		lblBcastIcon.setForeground(AntDesignTheme.PRIMARY);

		txtBroadcastMsg = new JTextField();
		txtBroadcastMsg.setFont(AntDesignTheme.FONT_REGULAR);
		txtBroadcastMsg.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(5, 8, 5, 8)));

		btnSendBroadcast = AntDesignTheme.createPrimaryButton("Gửi toàn bộ");
		btnSendBroadcast.addActionListener(e -> sendBroadcastMessage());
		txtBroadcastMsg.addActionListener(e -> sendBroadcastMessage());

		broadcastBar.add(lblBcastIcon, BorderLayout.WEST);
		broadcastBar.add(txtBroadcastMsg, BorderLayout.CENTER);
		broadcastBar.add(btnSendBroadcast, BorderLayout.EAST);

		card.add(broadcastBar, BorderLayout.SOUTH);
		return card;
	}

	private JPanel createLogsPanel() {
		JPanel card = AntDesignTheme.createCard("");

		txtLogs = new JTextArea();
		txtLogs.setEditable(false);
		txtLogs.setBackground(new Color(0x1E, 0x1E, 0x1E));
		txtLogs.setForeground(new Color(0xD4, 0xD4, 0xD4));
		txtLogs.setFont(new Font("Consolas", Font.PLAIN, 13));
		txtLogs.setBorder(new EmptyBorder(10, 10, 10, 10));

		JScrollPane scroll = new JScrollPane(txtLogs);
		scroll.setBorder(BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1));
		card.add(scroll, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		actions.setOpaque(false);
		JButton btnClear = AntDesignTheme.createDefaultButton("Xoá nhật ký");
		btnClear.addActionListener(e -> txtLogs.setText(""));
		actions.add(btnClear);

		card.add(actions, BorderLayout.SOUTH);
		return card;
	}

	private void toggleServer() {
		if (controller.isRunning()) {
			controller.stopServer();
		} else {
			try {
				int port = Integer.parseInt(txtPort.getText().trim());
				controller.startServer(port);
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(
						this, "Cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void showAddUserDialog() {
		JTextField uField = new JTextField();
		JPasswordField pField = new JPasswordField();
		Object[] msg = {"Tên tài khoản (Username):", uField, "Mật khẩu:", pField};
		int opt =
				JOptionPane.showConfirmDialog(
						this, msg, "Thêm tài khoản mới", JOptionPane.OK_CANCEL_OPTION);
		if (opt == JOptionPane.OK_OPTION) {
			String username = uField.getText().trim();
			String text = new String(pField.getPassword()).trim();
			if (username.isEmpty() || text.isEmpty()) {
				JOptionPane.showMessageDialog(
						this,
						"Tên tài khoản và mật khẩu không được rỗng!",
						"Lỗi",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (!controller.registerAccount(username, text)) {
				JOptionPane.showMessageDialog(
						this, "Tài khoản đã tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void showResetPasswordDialog() {
		int row = userTable.getSelectedRow();
		if (row == -1) {
			JOptionPane.showMessageDialog(
					this,
					"Vui lòng chọn tài khoản từ bảng!",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String username = (String) userTableModel.getValueAt(row, 0);
		String newPass =
				JOptionPane.showInputDialog(this, "Nhập mật khẩu mới cho " + username + ":");
		if (newPass != null && !newPass.trim().isEmpty()) {
			controller.resetPassword(username, newPass.trim());
			refreshUserTable(controller.getUserAccounts(), controller.getOnlineUserIps());
			JOptionPane.showMessageDialog(
					this, "Đã cập nhật mật khẩu cho " + username + " thành công!");
		}
	}

	private void handleKickUser() {
		int row = userTable.getSelectedRow();
		if (row == -1) {
			JOptionPane.showMessageDialog(
					this,
					"Vui lòng chọn tài khoản đang Online!",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String username = (String) userTableModel.getValueAt(row, 0);
		if (!controller.isUserOnline(username)) {
			JOptionPane.showMessageDialog(
					this,
					"Người dùng '" + username + "' đang Offline.",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		controller.kickUser(username);
	}

	private void handleDeleteUser() {
		int row = userTable.getSelectedRow();
		if (row == -1) {
			JOptionPane.showMessageDialog(
					this,
					"Vui lòng chọn tài khoản cần xoá!",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String username = (String) userTableModel.getValueAt(row, 0);
		int opt =
				JOptionPane.showConfirmDialog(
						this,
						"Bạn có chắc chắn muốn xoá tài khoản '" + username + "'?",
						"Xác nhận",
						JOptionPane.YES_NO_OPTION);
		if (opt == JOptionPane.YES_OPTION) {
			controller.deleteAccount(username);
		}
	}

	private void showAddGroupDialog() {
		String groupName = JOptionPane.showInputDialog(this, "Nhập tên nhóm mới:");
		if (groupName != null && !groupName.trim().isEmpty()) {
			controller.createNewGroup(groupName.trim(), "admin");
			controller.broadcastGroupList();
		}
	}

	private void handleDeleteGroup() {
		int row = groupTable.getSelectedRow();
		if (row == -1) {
			JOptionPane.showMessageDialog(
					this,
					"Vui lòng chọn nhóm cần xoá!",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String groupId = (String) groupTableModel.getValueAt(row, 0);
		int opt =
				JOptionPane.showConfirmDialog(
						this,
						"Bạn có chắc chắn muốn giải tán nhóm này?",
						"Xác nhận",
						JOptionPane.YES_NO_OPTION);
		if (opt == JOptionPane.YES_OPTION) {
			controller.deleteGroup(groupId);
		}
	}

	private void sendBroadcastMessage() {
		String content = txtBroadcastMsg.getText().trim();
		if (content.isEmpty()) {
			return;
		}
		Message bcast =
				new Message(
						Message.Type.SYSTEM_NOTIFICATION,
						"SERVER_ADMIN",
						"ALL",
						"[THÔNG BÁO] " + content);
		controller.broadcastToAll(bcast);
		controller.recordChat(ChatRecord.TargetType.DIRECT, "SERVER_ADMIN", "ALL", content);
		txtBroadcastMsg.setText("");
	}

	private void refreshUserTable(
			Map<String, UserAccount> accounts, Map<String, String> onlineUserIps) {
		SwingUtilities.invokeLater(
				() -> {
					userTableModel.setRowCount(0);
					for (Map.Entry<String, UserAccount> entry : accounts.entrySet()) {
						String username = entry.getKey();
						UserAccount acc = entry.getValue();
						boolean isOnline =
								onlineUserIps != null && onlineUserIps.containsKey(username);
						String ipAddress = isOnline ? onlineUserIps.get(username) : "-";
						userTableModel.addRow(
								new Object[] {
									username,
									isOnline ? "ONLINE" : "OFFLINE",
									ipAddress,
									acc.getLastLoginTime(),
									acc.getLastLogoutTime(),
									acc.getPassword(),
									acc.getCreatedAt()
								});
					}
					lblAccountStat.setText(String.valueOf(accounts.size()));
					lblOnlineStat.setText(
							String.valueOf(onlineUserIps != null ? onlineUserIps.size() : 0));
				});
	}

	private void refreshGroupTable(Map<String, GroupInfo> groups) {
		SwingUtilities.invokeLater(
				() -> {
					groupTableModel.setRowCount(0);
					for (GroupInfo g : groups.values()) {
						groupTableModel.addRow(
								new Object[] {
									g.getGroupId(),
									g.getGroupName(),
									g.getMulticastAddress(),
									g.getMulticastPort(),
									g.getMembers().size(),
									String.join(", ", g.getMembers())
								});
					}
					lblGroupStat.setText(String.valueOf(groups.size()));
				});
	}

	// ServerEventListener implementations
	@Override
	public void onServerStarted(int port) {
		SwingUtilities.invokeLater(
				() -> {
					btnStartStop.setText("Dừng Server");
					btnStartStop.setBackground(AntDesignTheme.ERROR);
					lblStatusBadge.setText("● ĐANG CHẠY : " + port);
					lblStatusBadge.setBackground(AntDesignTheme.SUCCESS_BG);
					lblStatusBadge.setForeground(AntDesignTheme.SUCCESS);
					txtPort.setEnabled(false);
					refreshUserTable(controller.getUserAccounts(), controller.getOnlineUserIps());
					refreshGroupTable(controller.getGroups());
				});
	}

	@Override
	public void onServerStopped() {
		SwingUtilities.invokeLater(
				() -> {
					btnStartStop.setText("Khởi động Server");
					btnStartStop.setBackground(AntDesignTheme.PRIMARY);
					lblStatusBadge.setText("● ĐÃ DỪNG");
					lblStatusBadge.setBackground(AntDesignTheme.ERROR_BG);
					lblStatusBadge.setForeground(AntDesignTheme.ERROR);
					txtPort.setEnabled(true);
					refreshUserTable(controller.getUserAccounts(), Collections.emptyMap());
				});
	}

	@Override
	public void onUserStatusChanged(String username, boolean online) {
		refreshUserTable(controller.getUserAccounts(), controller.getOnlineUserIps());
	}

	@Override
	public void onUserListUpdated(
			Map<String, UserAccount> accounts, Map<String, String> onlineUserIps) {
		refreshUserTable(accounts, onlineUserIps);
	}

	@Override
	public void onGroupListUpdated(Map<String, GroupInfo> groups) {
		refreshGroupTable(groups);
	}

	@Override
	public void onMessageLogged(ChatRecord record) {
		SwingUtilities.invokeLater(
				() -> {
					messageTableModel.addRow(
							new Object[] {
								record.getFormattedTime(),
								record.getTargetType(),
								record.getSender(),
								record.getTarget(),
								record.getMessage()
							});
					lblMsgStat.setText(String.valueOf(messageTableModel.getRowCount()));
				});
	}

	@Override
	public void onSystemLog(String log) {
		SwingUtilities.invokeLater(
				() -> {
					String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
					txtLogs.append("[" + timestamp + "] " + log + "\n");
					txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
				});
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(
				() -> {
					try {
						ServerUI ui = new ServerUI();
						ui.setVisible(true);
					} catch (IllegalStateException exception) {
						JOptionPane.showMessageDialog(null, exception.getMessage(), "Không mở được server", JOptionPane.ERROR_MESSAGE);
					}
				});
	}
}
