import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders actual Swing clients for visual inspection, without network actions. */
public class UiSmoke {
	static void checkVisiblePassword(java.awt.Container parent) {
		for (java.awt.Component c : parent.getComponents()) {
			if (c instanceof javax.swing.JPasswordField)
				throw new AssertionError("Password must be visible");
			if (c instanceof java.awt.Container container) checkVisiblePassword(container);
		}
	}

	public static void main(String[] args) throws Exception {
		if (!common.DesktopUi.ipOnly("/127.0.0.1:54321").equals("127.0.0.1")
				|| !common.DesktopUi.ipOnly("[::1]:54321").equals("::1"))
			throw new AssertionError("IP must not contain a port");
		Path folder = Path.of(args[0]);
		Files.createDirectories(folder);
		for (String name : new String[] {"client.ClientUI", "server.ServerUI"}) {
			Class.forName(name)
					.getMethod("main", String[].class)
					.invoke(null, (Object) new String[0]);
			SwingUtilities.invokeAndWait(() -> {});
			Thread.sleep(200);
			SwingUtilities.invokeAndWait(
					() -> {
						for (Frame f : Frame.getFrames())
							if (f.isVisible()) {
								if (f instanceof common.ClientWindow client)
									client.email.setText("alice@localhost");
								checkVisiblePassword(f);
								BufferedImage img =
										new BufferedImage(
												f.getWidth(),
												f.getHeight(),
												BufferedImage.TYPE_INT_RGB);
								Graphics2D g = img.createGraphics();
								f.paintAll(g);
								g.dispose();
								try {
									ImageIO.write(
											img,
											"png",
											folder.resolve(
															name.endsWith("ServerUI")
																	? "server.png"
																	: "client.png")
													.toFile());
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
								f.dispose();
							}
					});
		}
		System.exit(0);
	}
}
