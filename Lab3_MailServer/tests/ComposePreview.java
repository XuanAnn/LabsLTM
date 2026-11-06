import client.*;
import server.*;
import common.*;
import common.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Exercises the actual composer and recipient picker against a local server/MySQL test schema. */
public final class ComposePreview {
	static JFrame owner;
	static ComposeDialog composer;

	static Component named(Container root, String name) {
		for (Component c : root.getComponents()) {
			if (name.equals(c.getName())) return c;
			if (c instanceof Container container) {
				Component child = named(container, name);
				if (child != null) return child;
			}
		}
		return null;
	}

	static JButton button(Container root, String text) {
		for (Component c : root.getComponents()) {
			if (c instanceof JButton b && text.equals(b.getText())) return b;
			if (c instanceof Container container) {
				JButton child = button(container, text);
				if (child != null) return child;
			}
		}
		return null;
	}

	static JDialog picker() {
		for (Window w : Window.getWindows())
			if (w instanceof JDialog d
					&& d.isVisible()
					&& d.getTitle().equals("Chọn người nhận từ máy chủ")) return d;
		return null;
	}

	static boolean pickerOpen() {
		boolean[] value = {false};
		try {
			SwingUtilities.invokeAndWait(() -> value[0] = picker() != null);
		} catch (Exception ignored) {
		}
		return value[0];
	}

	static void capture(Window frame, String file) {
		try {
			BufferedImage image =
					new BufferedImage(
							frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			frame.paintAll(g);
			g.dispose();
			Files.createDirectories(Path.of("docs"));
			ImageIO.write(image, "png", Path.of("docs", file).toFile());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static void fields(String subject, String body) {
		((JTextField) named(composer, "subject")).setText(subject);
		((JTextArea) named(composer, "body")).setText(body);
	}

	public static void main(String[] args) throws Exception {
		Files.createDirectories(Path.of("target"));
		Path folder = Files.createTempDirectory(Path.of("target"), "compose-ui-").toAbsolutePath();
		IntegrationTest.dir = folder;
		Path config = TestDatabase.create(folder, "ui");
		IntegrationTest.mysqlConfig = config;
		System.setProperty("lab.db.config", config.toString());
		int smtp = IntegrationTest.port(), pop = IntegrationTest.port();
		Db db = new Db(config);
		try {
			IntegrationTest.start("server.MailServer", "" + smtp, "" + pop, config.toString());
			IntegrationTest.ready(smtp);
			IntegrationTest.ready(pop);
			MailClient.register("localhost", pop, "carol", "123");
			MailClient.register("localhost", pop, "dave", "123");
			ClientController controller = new ClientController();
			controller.login(new ClientWindow.Login("localhost", smtp, pop, "alice", "123"));
			AtomicBoolean sent = new AtomicBoolean();
			SwingUtilities.invokeAndWait(
					() -> {
						DesktopUi.init();
						owner = new JFrame("Hộp thư");
						try {
							composer =
									new ComposeDialog(
											owner, controller, false, () -> sent.set(true));
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
						((JComboBox<?>) named(composer, "send-mode"))
								.setSelectedItem(ComposeDialog.Mode.GROUP);
						composer.setVisible(true);
						fields(
								"Thông báo cho nhóm",
								"Xin chào Bob và Carol!\n"
										+ "Đây là thư gửi đến các thành viên được chọn.");
					});
			IntegrationTest.until(
					() -> {
						boolean[] ready = {false};
						try {
							SwingUtilities.invokeAndWait(
									() ->
											ready[0] =
													named(composer, "choose-recipients")
															.isEnabled());
						} catch (Exception ignored) {
						}
						return ready[0];
					});
			SwingUtilities.invokeAndWait(
					() -> ((JButton) named(composer, "choose-recipients")).doClick());
			IntegrationTest.until(ComposePreview::pickerOpen);
			SwingUtilities.invokeAndWait(
					() -> {
						JDialog dialog = picker();
						JTable table = (JTable) named(dialog, "recipient-list");
						for (int r = 0; r < table.getModel().getRowCount(); r++) {
							String user = table.getModel().getValueAt(r, 1).toString();
							table.getModel()
									.setValueAt(user.equals("bob") || user.equals("carol"), r, 0);
						}
						capture(dialog, "recipient-picker.png");
						button(dialog, "Xác nhận").doClick();
						capture(composer, "compose-group.png");
						((JButton) named(composer, "send-message")).doClick();
					});
			IntegrationTest.until(sent::get);
			IntegrationTest.check(
					IntegrationTest.inboxCount(db, "bob") == 1
							&& IntegrationTest.inboxCount(db, "carol") == 1
							&& IntegrationTest.inboxCount(db, "dave") == 0,
					"Actual recipient picker sends to selected group only");
			sent.set(false);
			SwingUtilities.invokeAndWait(
					() -> {
						try {
							composer =
									new ComposeDialog(
											owner, controller, false, () -> sent.set(true));
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
						((JComboBox<?>) named(composer, "send-mode"))
								.setSelectedItem(ComposeDialog.Mode.ALL);
						composer.setVisible(true);
						fields(
								"Thông báo toàn bộ",
								"Thư gửi đến mọi tài khoản khác trên máy chủ local.");
					});
			IntegrationTest.until(
					() -> {
						boolean[] ready = {false};
						try {
							SwingUtilities.invokeAndWait(
									() ->
											ready[0] =
													((JLabel) named(composer, "recipient-hint"))
															.getText()
															.contains("3 tài khoản"));
						} catch (Exception ignored) {
						}
						return ready[0];
					});
			SwingUtilities.invokeAndWait(
					() -> {
						if (((JTextField) named(composer, "recipients")).isEditable())
							throw new AssertionError("Broadcast target must be server-selected");
						capture(composer, "compose-broadcast.png");
						((JButton) named(composer, "send-message")).doClick();
					});
			IntegrationTest.until(sent::get);
			IntegrationTest.check(
					IntegrationTest.inboxCount(db, "bob") == 2
							&& IntegrationTest.inboxCount(db, "carol") == 2
							&& IntegrationTest.inboxCount(db, "dave") == 1
							&& IntegrationTest.inboxCount(db, "alice") == 0,
					"Actual broadcast composer sends to all other local accounts");
			System.out.println("COMPOSER UI CHECKS PASSED");
		} finally {
			SwingUtilities.invokeAndWait(
					() -> {
						for (Window w : Window.getWindows()) w.dispose();
					});
			for (Process p : IntegrationTest.processes) if (p.isAlive()) IntegrationTest.stop(p);
			TestDatabase.cleanup();
		}
		System.exit(0);
	}
}
