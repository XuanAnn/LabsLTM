package common;

import client.*;
import server.*;
import common.*;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

/** Lab 1 style login card -> workspace with account, server and client-IP badges. */
public final class ClientWindow extends JFrame {
	public record Login(String host, int port, int extraPort, String user, String password) {}

	public final JTextField host = DesktopUi.field("127.0.0.1", 16),
			port = DesktopUi.field("", 6),
			extraPort = DesktopUi.field("", 6),
			user = DesktopUi.field("alice", 14),
			password = DesktopUi.field("123", 14);
	public final JButton login = DesktopUi.button("Đăng nhập", true),
			register = DesktopUi.button("Đăng ký tài khoản mới", false);
	public final JLabel status = new JLabel(" ");
	private final CardLayout cards = new CardLayout();
	private final JPanel root = new JPanel(cards);
	private final String title;
	private Runnable logout = () -> {};

	public ClientWindow(String title, int defaultPort, int secondPort, boolean account) {
		super(title);
		this.title = title;
		DesktopUi.init();
		DesktopUi.window(this);
		port.setText("" + defaultPort);
		extraPort.setText("" + secondPort);
		root.setBackground(AntTheme.BG);
		JPanel outer = new JPanel(new GridBagLayout());
		outer.setBackground(AntTheme.BG);
		JPanel card = DesktopUi.card(title);
		card.setPreferredSize(new Dimension(490, account ? 450 : 300));
		java.util.List<String> names =
				new java.util.ArrayList<>(java.util.List.of("Địa chỉ máy chủ", "Cổng"));
		java.util.List<JComponent> inputs =
				new java.util.ArrayList<>(java.util.List.of(host, port));
		if (secondPort > 0) {
			names.add("Cổng nhận thư");
			inputs.add(extraPort);
		}
		if (account) {
			names.add("Tài khoản");
			inputs.add(user);
			names.add("Mật khẩu");
			inputs.add(password);
		}
		JPanel form =
				DesktopUi.form(names.toArray(String[]::new), inputs.toArray(JComponent[]::new));
		card.add(form, BorderLayout.CENTER);
		JPanel actions = new JPanel(new GridLayout(account ? 3 : 2, 1, 0, 9));
		actions.setBackground(Color.WHITE);
		if (!account) {
			login.setText("Kết nối");
		}
		actions.add(login);
		if (account) {
			actions.add(register);
		}
		status.setForeground(AntDesignTheme.ERROR);
		actions.add(status);
		card.add(actions, BorderLayout.SOUTH);
		outer.add(card);
		root.add(outer, "login");
		add(root);
		addWindowListener(
				new WindowAdapter() {
					public void windowClosed(WindowEvent event) {
						logout.run();
					}
				});
	}

	public Login values() {
		return new Login(
				DesktopUi.value(host),
				Integer.parseInt(DesktopUi.value(port)),
				Integer.parseInt(DesktopUi.value(extraPort)),
				DesktopUi.value(user),
				DesktopUi.value(password));
	}

	public void action(JButton button, DesktopUi.Action action) {
		DesktopUi.work(this, button, action);
	}

	public void open(
			JComponent content,
			String account,
			String endpoint,
			String clientIp,
			Runnable onLogout) {
		SwingUtilities.invokeLater(
				() -> {
					logout = onLogout;
					JButton back = DesktopUi.button("Đăng xuất", false);
					JPanel head =
							DesktopUi.header(
									title,
									"Quản lý dữ liệu",
									DesktopUi.row(
											DesktopUi.tag(account, true),
											DesktopUi.tag("Máy chủ: " + endpoint, true),
											new JLabel("IP client: " + clientIp),
											back));
					JPanel work = new JPanel(new BorderLayout(0, 14));
					work.setBackground(AntTheme.BG);
					work.add(head, BorderLayout.NORTH);
					content.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
					work.add(content, BorderLayout.CENTER);
					root.add(work, "work");
					back.addActionListener(
							e -> {
								logout.run();
								root.remove(work);
								cards.show(root, "login");
							});
					cards.show(root, "work");
				});
	}

	public void notice(String text) {
		SwingUtilities.invokeLater(() -> status.setText(text));
	}
}
