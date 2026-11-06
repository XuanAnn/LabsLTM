import common.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Opens the real Swing client against a real protocol server and MySQL test schema. */
public class WorkspacePreview {
	static ClientWindow window;

	static boolean open() {
		boolean[] ok = {false};
		try {
			SwingUtilities.invokeAndWait(
					() -> {
						for (Component p : window.getContentPane().getComponents())
							if (p instanceof JPanel root && root.getComponentCount() > 1)
								ok[0] = root.getComponent(root.getComponentCount() - 1).isVisible();
					});
		} catch (Exception ignored) {
		}
		return ok[0];
	}

	static JButton button(Container root, String text) {
		for (Component c : root.getComponents()) {
			if (c instanceof JButton b && b.getText().equals(text)) return b;
			if (c instanceof Container p) {
				JButton b = button(p, text);
				if (b != null) return b;
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		Files.createDirectories(Path.of("target"));
		Path folder = Files.createTempDirectory(Path.of("target"), "workspace-").toAbsolutePath();
		IntegrationTest.dir = folder;
		Path config = TestDatabase.create(folder, "ui");
		IntegrationTest.mysqlConfig = config;
		System.setProperty("lab.db.config", config.toString());
		int port = IntegrationTest.port(), extra = IntegrationTest.port();
		try {
			IntegrationTest.start("server.FtpServer", "" + port, folder.resolve("ftp").toString());
			IntegrationTest.ready(port);
			try (var c = new client.FtpClient("localhost", port, "student", "student123")) {
				c.upload(
						"bao-cao-lab.txt",
						"Bài thực hành FTP qua Java Socket"
								.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
			IntegrationTest.ready(port);
			client.ClientUI.main(new String[0]);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
					() -> {
						for (Frame f : Frame.getFrames())
							if (f instanceof ClientWindow w && w.isVisible()) window = w;
						if (window == null) throw new AssertionError("Client window missing");
						window.host.setText("127.0.0.1");
						window.port.setText("" + port);
						window.extraPort.setText("" + extra);
						window.login.doClick();
					});
			IntegrationTest.until(WorkspacePreview::open);

			Thread.sleep(1100);
			SwingUtilities.invokeAndWait(
					() -> {
						BufferedImage image =
								new BufferedImage(
										window.getWidth(),
										window.getHeight(),
										BufferedImage.TYPE_INT_RGB);
						Graphics2D g = image.createGraphics();
						window.paintAll(g);
						g.dispose();
						try {
							Files.createDirectories(Path.of("docs"));
							ImageIO.write(image, "png", Path.of("docs/workspace.png").toFile());
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
						window.dispose();
					});
			System.out.println("PASS real client workspace: ftp");
		} finally {
			if (window != null) SwingUtilities.invokeAndWait(() -> window.dispose());
			for (Process p : IntegrationTest.processes) if (p.isAlive()) IntegrationTest.stop(p);
			TestDatabase.cleanup();
		}
		System.exit(0);
	}
}
