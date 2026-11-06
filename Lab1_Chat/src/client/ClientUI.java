package client;

import common.AntDesignTheme;
import common.GroupInfo;
import common.UserStatus;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class ClientUI extends JFrame implements ClientController.ClientEventListener {
	private ClientController controller;

	private CardLayout cardLayout;
	private JPanel mainContainer;

	// Login Form Components
	private JTextField txtHost;
	private JTextField txtPort;
	private JTextField txtUsername;
	private JTextField txtPassword;
	private JButton btnLogin;
	private JButton btnRegister;
	private JLabel lblLoginStatus;

	// Chat Layout Components
	private JLabel lblCurrentUserBadge;
	private JLabel lblServerIpBadge;
	private JLabel lblLocalIpBadge;
	private JButton btnLogout;

	// Left Navigation (Users & Groups)
	private DefaultListModel<UserStatus> userListModel;
	private JList<UserStatus> userJList;
	private DefaultListModel<GroupInfo> groupListModel;
	private JList<GroupInfo> groupJList;
	private JButton btnCreateGroup;
	private JButton btnToggleGroupJoin;

	// Right Conversation Area
	private JLabel lblChatAvatar;
	private JLabel lblChatTitle;
	private JLabel lblChatSubtitle;
	private JLabel lblChatTag;
	private JTextPane chatPane;
	private JTextField txtMessage;
	private JButton btnSend;
	private JButton btnSendFile;

	// Chat State
	private enum ChatTargetType {
		NONE,
		USER,
		GROUP
	}

	private ChatTargetType currentTargetType = ChatTargetType.NONE;
	private String currentTargetId = null;

	private final Map<String, StringBuilder> chatHistories = new HashMap<>();

	public ClientUI() {
		super("Trò chuyện");
		AntDesignTheme.setup();
		controller = new ClientController(this);
		initComponents();
	}

	private void initComponents() {
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(
				new WindowAdapter() {
					@Override
					public void windowClosing(WindowEvent event) {
						handleExit();
					}
				});

		setSize(1000, 680);
		setMinimumSize(new Dimension(850, 550));
		setLocationRelativeTo(null);

		cardLayout = new CardLayout();
		mainContainer = new JPanel(cardLayout);
		mainContainer.setBackground(AntDesignTheme.BG_LAYOUT);

		mainContainer.add(createLoginPanel(), "LOGIN");
		mainContainer.add(createChatPanel(), "CHAT");

		add(mainContainer);
		cardLayout.show(mainContainer, "LOGIN");
	}

	private JPanel createLoginPanel() {
		JPanel outer = new JPanel(new GridBagLayout());
		outer.setBackground(AntDesignTheme.BG_LAYOUT);

		// Ant Design Modal / Card Box
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(32, 40, 32, 40)));
		card.setBackground(Color.WHITE);
		card.setPreferredSize(new Dimension(420, 400));

		JLabel lblTitle = new JLabel("Đăng nhập");
		lblTitle.setFont(AntDesignTheme.FONT_TITLE_LARGE);
		lblTitle.setForeground(AntDesignTheme.TEXT_TITLE);
		lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel lblSub = new JLabel("Đăng nhập để kết nối mạng trò chuyện ");
		lblSub.setFont(AntDesignTheme.FONT_REGULAR);
		lblSub.setForeground(AntDesignTheme.TEXT_MUTED);
		lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Host & Port
		JPanel netPanel = new JPanel(new GridLayout(1, 2, 10, 0));
		netPanel.setBackground(Color.WHITE);
		netPanel.setMaximumSize(new Dimension(340, 55));

		JPanel hostBox = new JPanel(new BorderLayout(0, 4));
		hostBox.setBackground(Color.WHITE);
		JLabel l1 = new JLabel("Máy chủ (Host):");
		l1.setFont(AntDesignTheme.FONT_SMALL);
		l1.setForeground(AntDesignTheme.TEXT_MUTED);
		txtHost = new JTextField("127.0.0.1");
		txtHost.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(5, 8, 5, 8)));
		hostBox.add(l1, BorderLayout.NORTH);
		hostBox.add(txtHost, BorderLayout.CENTER);

		JPanel portBox = new JPanel(new BorderLayout(0, 4));
		portBox.setBackground(Color.WHITE);
		JLabel l2 = new JLabel("Cổng (Port):");
		l2.setFont(AntDesignTheme.FONT_SMALL);
		l2.setForeground(AntDesignTheme.TEXT_MUTED);
		txtPort = new JTextField("8888");
		txtPort.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(5, 8, 5, 8)));
		portBox.add(l2, BorderLayout.NORTH);
		portBox.add(txtPort, BorderLayout.CENTER);

		netPanel.add(hostBox);
		netPanel.add(portBox);

		// Username
		JPanel userBox = new JPanel(new BorderLayout(0, 4));
		userBox.setBackground(Color.WHITE);
		userBox.setMaximumSize(new Dimension(340, 55));
		JLabel l3 = new JLabel("Tên tài khoản (Username):");
		l3.setFont(AntDesignTheme.FONT_SMALL);
		l3.setForeground(AntDesignTheme.TEXT_MUTED);
		txtUsername = new JTextField("");
		txtUsername.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(5, 8, 5, 8)));
		userBox.add(l3, BorderLayout.NORTH);
		userBox.add(txtUsername, BorderLayout.CENTER);

		// Password
		JPanel passBox = new JPanel(new BorderLayout(0, 4));
		passBox.setBackground(Color.WHITE);
		passBox.setMaximumSize(new Dimension(340, 55));
		JLabel l4 = new JLabel("Mật khẩu:");
		l4.setFont(AntDesignTheme.FONT_SMALL);
		l4.setForeground(AntDesignTheme.TEXT_MUTED);
		txtPassword = new JTextField("");
		txtPassword.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(5, 8, 5, 8)));
		passBox.add(l4, BorderLayout.NORTH);
		passBox.add(txtPassword, BorderLayout.CENTER);

		// Actions
		btnLogin = AntDesignTheme.createPrimaryButton("Đăng nhập");
		btnLogin.setMaximumSize(new Dimension(340, 38));
		btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);

		btnRegister = AntDesignTheme.createDefaultButton("Đăng ký tài khoản mới");
		btnRegister.setMaximumSize(new Dimension(340, 34));
		btnRegister.setAlignmentX(Component.CENTER_ALIGNMENT);

		lblLoginStatus = new JLabel(" ");
		lblLoginStatus.setFont(AntDesignTheme.FONT_SMALL);
		lblLoginStatus.setForeground(AntDesignTheme.ERROR);
		lblLoginStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

		btnLogin.addActionListener(e -> performLogin());
		btnRegister.addActionListener(e -> performRegister());
		txtUsername.addActionListener(e -> performLogin());
		txtPassword.addActionListener(e -> performLogin());

		// Card assembly
		card.add(Box.createVerticalStrut(4));
		card.add(lblTitle);
		card.add(Box.createVerticalStrut(4));
		card.add(lblSub);
		card.add(Box.createVerticalStrut(20));
		card.add(netPanel);
		card.add(Box.createVerticalStrut(10));
		card.add(userBox);
		card.add(Box.createVerticalStrut(10));
		card.add(passBox);
		card.add(Box.createVerticalStrut(20));
		card.add(btnLogin);
		card.add(Box.createVerticalStrut(8));
		card.add(btnRegister);
		card.add(Box.createVerticalStrut(14));
		card.add(lblLoginStatus);

		outer.add(card);
		return outer;
	}

	private JPanel createChatPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.setBackground(AntDesignTheme.BG_LAYOUT);
		panel.setBorder(new EmptyBorder(10, 12, 12, 12));

		// Top Header
		JPanel topHeader = new JPanel(new BorderLayout());
		topHeader.setBackground(Color.WHITE);
		topHeader.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(10, 18, 10, 18)));

		JPanel leftUserInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		leftUserInfo.setOpaque(false);
		JLabel userAvatar = new JLabel("👤");
		userAvatar.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

		lblCurrentUserBadge = new JLabel("Đang đăng nhập: User");
		lblCurrentUserBadge.setFont(AntDesignTheme.FONT_BOLD);
		lblCurrentUserBadge.setForeground(AntDesignTheme.TEXT_TITLE);

		leftUserInfo.add(userAvatar);
		leftUserInfo.add(lblCurrentUserBadge);

		// Right side: IP badges + Logout button
		JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		headerRight.setOpaque(false);

		lblServerIpBadge =
				AntDesignTheme.createTag(
						"🌐 Server: 127.0.0.1:8888",
						AntDesignTheme.PRIMARY_BG,
						AntDesignTheme.PRIMARY,
						AntDesignTheme.PRIMARY_HOVER);
		lblLocalIpBadge =
				AntDesignTheme.createTag(
						"💻 Client IP: 127.0.0.1",
						AntDesignTheme.SUCCESS_BG,
						AntDesignTheme.SUCCESS,
						AntDesignTheme.SUCCESS_BORDER);

		btnLogout = AntDesignTheme.createDefaultButton("Đăng xuất");
		btnLogout.addActionListener(e -> performLogout());

		headerRight.add(lblServerIpBadge);
		headerRight.add(lblLocalIpBadge);
		headerRight.add(btnLogout);

		topHeader.add(leftUserInfo, BorderLayout.WEST);
		topHeader.add(headerRight, BorderLayout.EAST);
		panel.add(topHeader, BorderLayout.NORTH);

		// Center SplitPane
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setDividerLocation(310);
		splitPane.setBorder(null);

		// Left Contacts & Groups Card
		JPanel leftCard = AntDesignTheme.createCard("");
		leftCard.setBorder(BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true));

		JTabbedPane leftTabs = new JTabbedPane();
		leftTabs.setFont(AntDesignTheme.FONT_BOLD);

		// Tab Contacts
		userListModel = new DefaultListModel<>();
		userJList = new JList<>(userListModel);
		userJList.setCellRenderer(new AntUserCellRenderer());
		userJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		userJList.addListSelectionListener(
				e -> {
					if (!e.getValueIsAdjusting()) {
						UserStatus selected = userJList.getSelectedValue();
						if (selected != null) {
							groupJList.clearSelection();
							selectTargetUser(selected.getUsername());
						}
					}
				});
		JScrollPane userScroll = new JScrollPane(userJList);
		userScroll.setBorder(null);
		leftTabs.addTab("👤 Danh bạ (1-1)", userScroll);

		// Tab Groups
		JPanel groupsTab = new JPanel(new BorderLayout());
		groupsTab.setBackground(Color.WHITE);
		groupListModel = new DefaultListModel<>();
		groupJList = new JList<>(groupListModel);
		groupJList.setCellRenderer(new AntGroupCellRenderer());
		groupJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		groupJList.addListSelectionListener(
				e -> {
					if (!e.getValueIsAdjusting()) {
						GroupInfo selected = groupJList.getSelectedValue();
						if (selected != null) {
							userJList.clearSelection();
							selectTargetGroup(selected);
						}
					}
				});
		JScrollPane groupScroll = new JScrollPane(groupJList);
		groupScroll.setBorder(null);
		groupsTab.add(groupScroll, BorderLayout.CENTER);

		// Group Action Buttons
		JPanel groupActions = new JPanel(new GridLayout(1, 2, 8, 0));
		groupActions.setBorder(new EmptyBorder(8, 4, 4, 4));
		groupActions.setOpaque(false);
		btnCreateGroup = AntDesignTheme.createPrimaryButton("+ Tạo nhóm");
		btnToggleGroupJoin = AntDesignTheme.createDefaultButton("Vào / Rời nhóm");

		btnCreateGroup.addActionListener(e -> showCreateGroupDialog());
		btnToggleGroupJoin.addActionListener(e -> toggleGroupMembership());

		groupActions.add(btnCreateGroup);
		groupActions.add(btnToggleGroupJoin);
		groupsTab.add(groupActions, BorderLayout.SOUTH);

		leftTabs.addTab("👥 Nhóm trò chuyện", groupsTab);
		leftCard.add(leftTabs, BorderLayout.CENTER);
		splitPane.setLeftComponent(leftCard);

		// Right Conversation Card
		JPanel rightCard = AntDesignTheme.createCard("");
		rightCard.setBorder(BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true));

		// Conversation Header
		JPanel chatHeader = new JPanel(new BorderLayout(10, 0));
		chatHeader.setBackground(Color.WHITE);
		chatHeader.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 0, 1, 0, AntDesignTheme.BORDER_SPLIT),
						new EmptyBorder(6, 12, 10, 12)));

		JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		headerLeft.setOpaque(false);

		lblChatAvatar = new JLabel("💬");
		lblChatAvatar.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));

		JPanel titleBox = new JPanel(new GridLayout(2, 1));
		titleBox.setOpaque(false);
		lblChatTitle = new JLabel("Chọn một liên hệ hoặc nhóm để bắt đầu");
		lblChatTitle.setFont(AntDesignTheme.FONT_TITLE);
		lblChatTitle.setForeground(AntDesignTheme.TEXT_TITLE);

		lblChatSubtitle = new JLabel("");
		lblChatSubtitle.setFont(AntDesignTheme.FONT_SMALL);
		lblChatSubtitle.setForeground(AntDesignTheme.TEXT_MUTED);

		titleBox.add(lblChatTitle);
		titleBox.add(lblChatSubtitle);

		headerLeft.add(lblChatAvatar);
		headerLeft.add(titleBox);

		lblChatTag =
				AntDesignTheme.createTag(
						"CHỜ KẾT NỐI",
						AntDesignTheme.BG_LAYOUT,
						AntDesignTheme.TEXT_MUTED,
						AntDesignTheme.BORDER);
		JPanel chatHeaderRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
		chatHeaderRight.setOpaque(false);
		chatHeaderRight.add(lblChatTag);

		chatHeader.add(headerLeft, BorderLayout.CENTER);
		chatHeader.add(chatHeaderRight, BorderLayout.EAST);
		rightCard.add(chatHeader, BorderLayout.NORTH);

		// Chat Pane
		chatPane = new JTextPane();
		chatPane.setContentType("text/html");
		chatPane.setEditable(false);
		chatPane.setBackground(new Color(0xFA, 0xFA, 0xFA));
		chatPane.setText(
				"<html><body style='font-family: Segoe UI, sans-serif; font-size: 13px; color:"
					+ " #8c8c8c; padding: 20px; text-align: center;'><div style='margin-top: 80px;"
					+ " font-size: 32px;'>💬</div><div style='font-weight: bold; margin-top: 10px;"
					+ " color: #262626;'>Chưa chọn hội thoại</div><div>Vui lòng chọn một người bạn"
					+ " hoặc tham gia nhóm ở cột bên trái để bắt đầu nhắn tin.</div>"
					+ "</body></html>");

		JScrollPane chatScroll = new JScrollPane(chatPane);
		chatScroll.setBorder(null);
		rightCard.add(chatScroll, BorderLayout.CENTER);

		// Message Input Footer
		JPanel inputBar = new JPanel(new BorderLayout(10, 0));
		inputBar.setBackground(Color.WHITE);
		inputBar.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(1, 0, 0, 0, AntDesignTheme.BORDER_SPLIT),
						new EmptyBorder(10, 10, 4, 10)));

		txtMessage = new JTextField();
		txtMessage.setFont(AntDesignTheme.FONT_REGULAR);
		txtMessage.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(AntDesignTheme.BORDER, 1, true),
						new EmptyBorder(7, 10, 7, 10)));
		txtMessage.setEnabled(false);

		btnSend = AntDesignTheme.createPrimaryButton("Gửi tin nhắn");
		btnSend.setEnabled(false);
		btnSend.addActionListener(e -> sendMessage());
		txtMessage.addActionListener(e -> sendMessage());

		btnSendFile = AntDesignTheme.createDefaultButton("📁 Gửi File (Broadcast)");
		btnSendFile.setEnabled(false);
		btnSendFile.setToolTipText("Gửi file phát sóng đến tất cả mọi người đang online");
		btnSendFile.addActionListener(e -> chooseAndBroadcastFile());

		JPanel rightBtnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		rightBtnBox.setOpaque(false);
		rightBtnBox.add(btnSendFile);
		rightBtnBox.add(btnSend);

		inputBar.add(txtMessage, BorderLayout.CENTER);
		inputBar.add(rightBtnBox, BorderLayout.EAST);
		rightCard.add(inputBar, BorderLayout.SOUTH);

		splitPane.setRightComponent(rightCard);
		panel.add(splitPane, BorderLayout.CENTER);
		return panel;
	}

	private void performLogin() {
		lblLoginStatus.setText("Đang kết nối...");
		lblLoginStatus.setForeground(AntDesignTheme.PRIMARY);

		String host = txtHost.getText().trim();
		int port;
		try {
			port = Integer.parseInt(txtPort.getText().trim());
		} catch (NumberFormatException exception) {
			lblLoginStatus.setText("Cổng máy chủ không hợp lệ");
			lblLoginStatus.setForeground(AntDesignTheme.ERROR);
			return;
		}

		String username = txtUsername.getText().trim();
		String password = new String(txtPassword.getText()).trim();

		if (username.isEmpty() || password.isEmpty()) {
			lblLoginStatus.setText("Vui lòng nhập tên tài khoản và mật khẩu");
			lblLoginStatus.setForeground(AntDesignTheme.ERROR);
			return;
		}

		new Thread(
						() -> {
							if (!controller.isConnected()) {
								if (!controller.connect(host, port)) {
									SwingUtilities.invokeLater(
											() -> {
												lblLoginStatus.setText(
														"Không thể kết nối đến máy chủ "
																+ host
																+ ":"
																+ port);
												lblLoginStatus.setForeground(AntDesignTheme.ERROR);
											});
									return;
								}
							}
							controller.login(username, password);
						})
				.start();
	}

	private void performRegister() {
		lblLoginStatus.setText("Đang đăng ký...");
		lblLoginStatus.setForeground(AntDesignTheme.PRIMARY);

		String host = txtHost.getText().trim();
		int port;
		try {
			port = Integer.parseInt(txtPort.getText().trim());
		} catch (NumberFormatException exception) {
			lblLoginStatus.setText("Cổng máy chủ không hợp lệ");
			lblLoginStatus.setForeground(AntDesignTheme.ERROR);
			return;
		}

		String username = txtUsername.getText().trim();
		String password = new String(txtPassword.getText()).trim();

		if (username.isEmpty() || password.isEmpty()) {
			lblLoginStatus.setText("Vui lòng nhập tài khoản và mật khẩu");
			lblLoginStatus.setForeground(AntDesignTheme.ERROR);
			return;
		}

		new Thread(
						() -> {
							if (!controller.isConnected()) {
								if (!controller.connect(host, port)) {
									SwingUtilities.invokeLater(
											() -> {
												lblLoginStatus.setText(
														"Không thể kết nối đến máy chủ "
																+ host
																+ ":"
																+ port);
												lblLoginStatus.setForeground(AntDesignTheme.ERROR);
											});
									return;
								}
							}
							controller.register(username, password);
						})
				.start();
	}

	private void performLogout() {
		controller.disconnect("Người dùng đăng xuất");
		cardLayout.show(mainContainer, "LOGIN");
		lblLoginStatus.setText("Đã đăng xuất.");
		lblLoginStatus.setForeground(AntDesignTheme.TEXT_MUTED);
		chatHistories.clear();
		currentTargetType = ChatTargetType.NONE;
		currentTargetId = null;
	}

	private void handleExit() {
		controller.disconnect("Thoát ứng dụng");
		System.exit(0);
	}

	private void selectTargetUser(String username) {
		if (username.equals(controller.getCurrentUsername())) {
			JOptionPane.showMessageDialog(
					this,
					"Bạn không thể tự nhắn tin cho chính mình.",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		currentTargetType = ChatTargetType.USER;
		currentTargetId = username;

		lblChatAvatar.setText("👤");
		lblChatTitle.setText(username);
		lblChatSubtitle.setText("Trò chuyện trực tiếp");
		lblChatTag.setText("TRỰC TIẾP");
		lblChatTag.setBackground(AntDesignTheme.PRIMARY_BG);
		lblChatTag.setForeground(AntDesignTheme.PRIMARY);

		txtMessage.setEnabled(true);
		btnSend.setEnabled(true);
		btnSendFile.setEnabled(true);
		txtMessage.requestFocusInWindow();
		renderHistory();
	}

	private void selectTargetGroup(GroupInfo group) {
		currentTargetType = ChatTargetType.GROUP;
		currentTargetId = group.getGroupId();

		boolean isMember = controller.isJoinedGroup(group.getGroupId());
		lblChatAvatar.setText("👥");
		lblChatTitle.setText(group.getGroupName());
		lblChatSubtitle.setText(
				"Địa chỉ nhóm: "
						+ group.getMulticastAddress()
						+ ":"
						+ group.getMulticastPort()
						+ " • "
						+ group.getMembers().size()
						+ " thành viên");

		if (isMember) {
			lblChatTag.setText("ĐÃ THAM GIA");
			lblChatTag.setBackground(AntDesignTheme.SUCCESS_BG);
			lblChatTag.setForeground(AntDesignTheme.SUCCESS);
		} else {
			lblChatTag.setText("CHƯA THAM GIA");
			lblChatTag.setBackground(AntDesignTheme.WARNING_BG);
			lblChatTag.setForeground(AntDesignTheme.WARNING);
		}

		txtMessage.setEnabled(isMember);
		btnSend.setEnabled(isMember);
		btnSendFile.setEnabled(isMember);
		if (isMember) {
			txtMessage.requestFocusInWindow();
		}
		renderHistory();
	}

	private void showCreateGroupDialog() {
		String groupName = JOptionPane.showInputDialog(this, "Nhập tên nhóm trò chuyện mới:");
		if (groupName != null && !groupName.trim().isEmpty()) {
			controller.createGroup(groupName.trim());
		}
	}

	private void toggleGroupMembership() {
		GroupInfo selected = groupJList.getSelectedValue();
		if (selected == null) {
			JOptionPane.showMessageDialog(
					this,
					"Vui lòng chọn một nhóm từ danh sách trước.",
					"Thông báo",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		boolean isJoined = controller.isJoinedGroup(selected.getGroupId());
		if (isJoined) {
			controller.requestLeaveGroup(selected);
			JOptionPane.showMessageDialog(this, "Đã rời nhóm: " + selected.getGroupName());
		} else {
			controller.requestJoinGroup(selected);
			JOptionPane.showMessageDialog(
					this, "Đã tham gia nhóm: " + selected.getGroupName() + "!");
		}

		selectTargetGroup(selected);
		groupJList.repaint();
	}

	private void sendMessage() {
		String text = txtMessage.getText().trim();
		if (text.isEmpty() || currentTargetType == ChatTargetType.NONE || currentTargetId == null) {
			return;
		}

		long now = System.currentTimeMillis();
		String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(now));

		if (currentTargetType == ChatTargetType.USER) {
			controller.sendDirectMessage(currentTargetId, text);
			appendChatMessage(
					currentTargetId, controller.getCurrentUsername(), text, timeStr, true);
		} else if (currentTargetType == ChatTargetType.GROUP) {
			GroupInfo group = getGroupById(currentTargetId);
			if (group != null) {
				controller.sendGroupMessage(group, text);
			}
		}
		txtMessage.setText("");
	}

	private void chooseAndBroadcastFile() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(
				"Chọn tập tin để Gửi Phát Sóng (Broadcast tới tất cả mọi người)");
		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			if (selectedFile != null && selectedFile.exists()) {
				if (selectedFile.length() > 50 * 1024 * 1024) { // 50MB limit
					JOptionPane.showMessageDialog(
							this,
							"Dung lượng file tối đa cho phép là 50MB.",
							"Cảnh báo",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				int confirm =
						JOptionPane.showConfirmDialog(
								this,
								"Bạn có chắc muốn gửi phát sóng file '"
										+ selectedFile.getName()
										+ "' tới TẤT CẢ mọi người trong hệ thống?",
								"Xác nhận Broadcast File",
								JOptionPane.YES_NO_OPTION);
				if (confirm == JOptionPane.YES_OPTION) {
					controller.broadcastFile(selectedFile);
				}
			}
		}
	}

	private GroupInfo getGroupById(String groupId) {
		for (int i = 0; i < groupListModel.size(); i++) {
			GroupInfo g = groupListModel.get(i);
			if (g.getGroupId().equals(groupId)) {
				return g;
			}
		}
		return null;
	}

	private synchronized void appendChatMessage(
			String conversationKey, String sender, String content, String timeStr, boolean isSelf) {
		StringBuilder sb = chatHistories.computeIfAbsent(conversationKey, k -> new StringBuilder());

		// Ant Design Card / Bubble Message Style
		String bubbleBg = isSelf ? "#1677ff" : "#ffffff";
		String textColor = isSelf ? "#ffffff" : "#262626";
		String metaColor = isSelf ? "rgba(255,255,255,0.75)" : "#8c8c8c";
		String border = isSelf ? "none" : "1px solid #d9d9d9";
		String align = isSelf ? "right" : "left";

		sb.append(
				String.format(
						"<div style='text-align: %s; margin: 8px 0;'><div style='display:"
							+ " inline-block; background-color: %s; color: %s; padding: 8px 14px;"
							+ " border-radius: 8px; border: %s; max-width: 72%%; text-align: left;"
							+ " box-shadow: 0 1px 2px rgba(0,0,0,0.05);'><div style='font-size:"
							+ " 11px; color: %s; margin-bottom: 3px;'><b>%s</b> &bull; %s</div><div"
							+ " style='font-size: 13px; line-height: 1.4; word-wrap:"
							+ " break-word;'>%s</div></div></div>",
						align,
						bubbleBg,
						textColor,
						border,
						metaColor,
						escapeHtml(sender),
						timeStr,
						escapeHtml(content)));

		if (conversationKey.equals(currentTargetId)) {
			renderHistory();
		}
	}

	private void renderHistory() {
		if (currentTargetId == null) {
			chatPane.setText("<html><body></body></html>");
			return;
		}

		StringBuilder history = chatHistories.get(currentTargetId);
		String body =
				history != null
						? history.toString()
						: "<div style='color: #bfbfbf; font-style: italic; text-align: center;"
								+ " margin-top: 30px;'>Chưa có tin nhắn nào. Hãy gửi lời chào đầu"
								+ " tiên!</div>";

		chatPane.setText(
				"<html><body style='font-family: Segoe UI, sans-serif; padding: 12px;"
						+ " background-color: #fafafa;'>"
						+ body
						+ "</body></html>");
		chatPane.setCaretPosition(chatPane.getDocument().getLength());
	}

	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("\n", "<br/>");
	}

	// ClientEventListener Implementations
	@Override
	public void onLoginResult(boolean success, String message) {
		SwingUtilities.invokeLater(
				() -> {
					if (success) {
						lblCurrentUserBadge.setText(
								"Tài khoản: " + controller.getCurrentUsername());
						lblServerIpBadge.setText("🌐 Server: " + controller.getServerAddress());
						lblLocalIpBadge.setText("💻 Client IP: " + controller.getLocalAddress());
						cardLayout.show(mainContainer, "CHAT");
					} else {
						lblLoginStatus.setText(message);
						lblLoginStatus.setForeground(AntDesignTheme.ERROR);
					}
				});
	}

	@Override
	public void onRegisterResult(boolean success, String message) {
		SwingUtilities.invokeLater(
				() -> {
					lblLoginStatus.setText(message);
					lblLoginStatus.setForeground(
							success ? AntDesignTheme.SUCCESS : AntDesignTheme.ERROR);
				});
	}

	@Override
	public void onUserListUpdated(List<UserStatus> users) {
		SwingUtilities.invokeLater(
				() -> {
					userListModel.clear();
					for (UserStatus userStatus : users) {
						if (!userStatus.getUsername().equals(controller.getCurrentUsername())) {
							userListModel.addElement(userStatus);
						}
					}
				});
	}

	@Override
	public void onGroupListUpdated(List<GroupInfo> groups) {
		SwingUtilities.invokeLater(
				() -> {
					groupListModel.clear();
					for (GroupInfo g : groups) {
						groupListModel.addElement(g);
					}
					if (currentTargetType == ChatTargetType.GROUP && currentTargetId != null) {
						GroupInfo g = getGroupById(currentTargetId);
						if (g != null) {
							selectTargetGroup(g);
						}
					}
				});
	}

	@Override
	public void onDirectMessageReceived(String sender, String message, long timestamp) {
		SwingUtilities.invokeLater(
				() -> {
					String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
					appendChatMessage(sender, sender, message, timeStr, false);
				});
	}

	@Override
	public void onGroupMessageReceived(
			String groupId, String sender, String message, long timestamp) {
		SwingUtilities.invokeLater(
				() -> {
					boolean isSelf = sender.equals(controller.getCurrentUsername());
					String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
					appendChatMessage(groupId, sender, message, timeStr, isSelf);
				});
	}

	@Override
	public void onFileReceived(String sender, String fileName, byte[] fileData, long timestamp) {
		SwingUtilities.invokeLater(
				() -> {
					boolean isSelf = sender.equals(controller.getCurrentUsername());
					String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
					String sizeStr = fileData != null ? formatSize(fileData.length) : "0 B";

					// Hiển thị bong bóng thông báo file trong cuộc hội thoại hiện tại
					String fileDisplayMsg =
							"📁 <b>[BROADCAST FILE]</b> " + fileName + " (" + sizeStr + ")";
					if (currentTargetId != null) {
						appendChatMessage(currentTargetId, sender, fileDisplayMsg, timeStr, isSelf);
					}

					if (!isSelf) {
						// Hộp thoại hỏi người nhận có muốn tải và lưu file về máy hay không
						int opt =
								JOptionPane.showConfirmDialog(
										this,
										"Người dùng '"
												+ sender
												+ "' vừa gửi phát sóng (broadcast) file:\n"
												+ "Tên file: "
												+ fileName
												+ "\nDung lượng: "
												+ sizeStr
												+ "\n\nBạn có muốn lưu file này về máy không?",
										"Nhận File Broadcast từ " + sender,
										JOptionPane.YES_NO_OPTION,
										JOptionPane.INFORMATION_MESSAGE);

						if (opt == JOptionPane.YES_OPTION) {
							saveReceivedFile(fileName, fileData);
						}
					} else {
						JOptionPane.showMessageDialog(
								this,
								"File '"
										+ fileName
										+ "' ("
										+ sizeStr
										+ ") đã được phát sóng thành công đến tất cả mọi người!",
								"Broadcast Thành công",
								JOptionPane.INFORMATION_MESSAGE);
					}
				});
	}

	private String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(1024));
		char pre = "KMGTPE".charAt(exp - 1);
		return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
	}

	private void saveReceivedFile(String defaultName, byte[] data) {
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new File(defaultName));
		chooser.setDialogTitle("Lưu file đã nhận từ Broadcast");
		int res = chooser.showSaveDialog(this);
		if (res == JFileChooser.APPROVE_OPTION) {
			File target = chooser.getSelectedFile();
			try (FileOutputStream fos = new FileOutputStream(target)) {
				fos.write(data);
				fos.flush();
				JOptionPane.showMessageDialog(
						this,
						"Đã lưu file thành công tại:\n" + target.getAbsolutePath(),
						"Thành công",
						JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException exception) {
				JOptionPane.showMessageDialog(
						this,
						"Lỗi khi lưu file: " + exception.getMessage(),
						"Lỗi",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	@Override
	public void onSystemNotification(String notification) {
		SwingUtilities.invokeLater(
				() -> {
					JOptionPane.showMessageDialog(
							this,
							notification,
							"Thông báo Hệ thống",
							JOptionPane.INFORMATION_MESSAGE);
				});
	}

	@Override
	public void onConnectionClosed(String reason) {
		SwingUtilities.invokeLater(
				() -> {
					JOptionPane.showMessageDialog(
							this, reason, "Ngắt kết nối", JOptionPane.WARNING_MESSAGE);
					cardLayout.show(mainContainer, "LOGIN");
					lblLoginStatus.setText(reason);
					lblLoginStatus.setForeground(AntDesignTheme.ERROR);
				});
	}

	// Ant Design Custom Cell Renderers
	private static class AntUserCellRenderer extends JPanel
			implements ListCellRenderer<UserStatus> {
		private JLabel lblAvatar = new JLabel("👤");
		private JLabel lblName = new JLabel();
		private JLabel lblTag = new JLabel();

		public AntUserCellRenderer() {
			setLayout(new BorderLayout(8, 0));
			setOpaque(true);
			setBorder(new EmptyBorder(8, 12, 8, 12));

			lblAvatar.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
			lblName.setFont(AntDesignTheme.FONT_BOLD);

			JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			left.setOpaque(false);
			left.add(lblAvatar);
			left.add(lblName);

			add(left, BorderLayout.WEST);
			add(lblTag, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(
				JList<? extends UserStatus> list,
				UserStatus value,
				int index,
				boolean isSelected,
				boolean cellHasFocus) {
			lblName.setText(value.getUsername());
			if (value.isOnline()) {
				lblTag.setText("Online");
				lblTag.setFont(AntDesignTheme.FONT_SMALL);
				lblTag.setForeground(AntDesignTheme.SUCCESS);
			} else {
				lblTag.setText("Offline");
				lblTag.setFont(AntDesignTheme.FONT_SMALL);
				lblTag.setForeground(AntDesignTheme.TEXT_MUTED);
			}

			if (isSelected) {
				setBackground(AntDesignTheme.PRIMARY_BG);
				lblName.setForeground(AntDesignTheme.PRIMARY);
			} else {
				setBackground(Color.WHITE);
				lblName.setForeground(AntDesignTheme.TEXT_TITLE);
			}
			return this;
		}
	}

	private class AntGroupCellRenderer extends JPanel implements ListCellRenderer<GroupInfo> {
		private JLabel lblIcon = new JLabel("👥");
		private JLabel lblName = new JLabel();
		private JLabel lblBadge = new JLabel();

		public AntGroupCellRenderer() {
			setLayout(new BorderLayout(8, 0));
			setOpaque(true);
			setBorder(new EmptyBorder(8, 12, 8, 12));

			lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
			lblName.setFont(AntDesignTheme.FONT_BOLD);

			JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			left.setOpaque(false);
			left.add(lblIcon);
			left.add(lblName);

			add(left, BorderLayout.WEST);
			add(lblBadge, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(
				JList<? extends GroupInfo> list,
				GroupInfo value,
				int index,
				boolean isSelected,
				boolean cellHasFocus) {
			boolean isJoined = controller.isJoinedGroup(value.getGroupId());
			lblName.setText(value.getGroupName() + " (" + value.getMembers().size() + ")");

			if (isJoined) {
				lblBadge.setText("Joined");
				lblBadge.setFont(AntDesignTheme.FONT_SMALL);
				lblBadge.setForeground(AntDesignTheme.PRIMARY);
			} else {
				lblBadge.setText("");
			}

			if (isSelected) {
				setBackground(AntDesignTheme.PRIMARY_BG);
				lblName.setForeground(AntDesignTheme.PRIMARY);
			} else {
				setBackground(Color.WHITE);
				lblName.setForeground(AntDesignTheme.TEXT_TITLE);
			}
			return this;
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(
				() -> {
					ClientUI ui = new ClientUI();
					ui.setVisible(true);
				});
	}
}
