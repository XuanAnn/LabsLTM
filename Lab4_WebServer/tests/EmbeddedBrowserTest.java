import com.google.gson.*;

import client.*;
import server.*;
import common.*;
import common.*;

import org.cef.browser.CefBrowser;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Real Swing client, native embedded browser, own HTTP server and isolated MySQL schema. */
public class EmbeddedBrowserTest {
	private static String readAddress(java.lang.reflect.Field field, Object pane) {
		try {
			return ((JTextField) field.get(pane)).getText();
		} catch (IllegalAccessException exception) {
			throw new RuntimeException(exception);
		}
	}

	static ClientWindow window;
	static EmbeddedBrowser pane;
	static CefBrowser browser;
	static int checks;
	static final java.util.List<Throwable> uncaught =
			new java.util.concurrent.CopyOnWriteArrayList<>();

	static void check(boolean value, String label) {
		if (!value) throw new AssertionError(label);
		checks++;
		System.out.println("PASS " + label);
	}

	static void until(BooleanSupplier condition) throws Exception {
		long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
		while (System.nanoTime() < end) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(100);
		}
		throw new AssertionError("Timed out");
	}

	static <T extends Component> T find(Container root, Class<T> type) {
		for (Component c : root.getComponents()) {
			if (type.isInstance(c)) return type.cast(c);
			if (c instanceof Container child) {
				T result = find(child, type);
				if (result != null) return result;
			}
		}
		return null;
	}

	static JButton button(Container root, String text) {
		for (Component c : root.getComponents()) {
			if (c instanceof JButton b && b.getText().equals(text)) return b;
			if (c instanceof Container child) {
				JButton b = button(child, text);
				if (b != null) return b;
			}
		}
		return null;
	}

	static JsonElement eval(String expression) throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("expression", expression);
		params.addProperty("returnByValue", true);
		params.addProperty("awaitPromise", true);
		params.addProperty("userGesture", true);
		JsonObject response =
				JsonParser.parseString(
								browser.getDevToolsClient()
										.executeDevToolsMethod(
												"Runtime.evaluate", params.toString())
										.get(20, TimeUnit.SECONDS))
						.getAsJsonObject();
		if (response.has("exceptionDetails")) throw new AssertionError(response.toString());
		JsonObject result = response.getAsJsonObject("result");
		return result.has("value") ? result.get("value") : JsonNull.INSTANCE;
	}

	static void waitJs(String expression) throws Exception {
		try {
			until(
					() -> {
						try {
							return eval(expression).getAsBoolean();
						} catch (Exception e) {
							return false;
						}
					});
		} catch (AssertionError e) {
			System.err.println("Failed condition: " + expression);
			System.err.println(
					browser.getDevToolsClient()
							.executeDevToolsMethod(
									"Runtime.evaluate",
									"{\"expression\":\"JSON.stringify({title:document.title,body:document.body?.innerText,audio:document.querySelector('audio')?.readyState,error:document.querySelector('audio')?.error?.message})\",\"returnByValue\":true}")
							.get(10, TimeUnit.SECONDS));
			screenshot("embedded-error.png");
			throw e;
		}
	}

	static void screenshot(String name) throws Exception {
		Thread.sleep(400);
		Rectangle[] rect = {null};
		SwingUtilities.invokeAndWait(
				() -> {
					window.toFront();
					rect[0] = new Rectangle(window.getLocationOnScreen(), window.getSize());
				});
		ImageIO.write(
				new Robot().createScreenCapture(rect[0]), "png", Path.of("docs", name).toFile());
	}

	static void section(String category) throws Exception {
		eval("document.querySelector('a[href=\"/" + category + "\"]').click();true");
		waitJs("location.pathname==='/" + category + "'");
	}

	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler(
				(thread, error) -> {
					uncaught.add(error);
					error.printStackTrace();
				});
		Path dir = Files.createTempDirectory(Path.of("target"), "embedded-").toAbsolutePath();
		Path config = TestDatabase.create(dir, "embedded");
		System.setProperty("lab.db.config", config.toString());
		System.setProperty("lab.browser.cache", dir.resolve("browser-cache").toString());
		IntegrationTest.dir = dir;
		IntegrationTest.mysqlConfig = config;
		int port = IntegrationTest.port();
		try {
			IntegrationTest.start(
					"server.WebServer",
					Integer.toString(port),
					Path.of("public").toAbsolutePath().toString());
			IntegrationTest.ready(port);
			ClientUI.main(new String[0]);
			SwingUtilities.invokeAndWait(
					() -> {
						for (Frame f : Frame.getFrames())
							if (f instanceof ClientWindow w && w.isVisible()) window = w;
						window.setLocation(0, 0);
						window.host.setText("127.0.0.1");
						window.port.setText(Integer.toString(port));
						window.login.doClick();
					});
			until(
					() -> {
						try {
							SwingUtilities.invokeAndWait(
									() -> pane = find(window, EmbeddedBrowser.class));
							return pane != null;
						} catch (Exception e) {
							return false;
						}
					});
			var field = EmbeddedBrowser.class.getDeclaredField("browser");
			field.setAccessible(true);
			until(
					() -> {
						try {
							browser = (CefBrowser) field.get(pane);
							return browser != null && browser.getIdentifier() > 0;
						} catch (Exception e) {
							return false;
						}
					});
			waitJs(
					"!!document.querySelector('audio') &&"
							+ " document.querySelector('audio').readyState>=1");
			check(
					eval("document.title==='Thư viện của bạn' && typeof load==='function'")
							.getAsBoolean(),
					"Real page and JavaScript inside Swing");
			check(
					eval("document.querySelector('#selected').textContent.includes('Moonlight')")
							.getAsBoolean(),
					"Real Beethoven recording is selected");
			eval("document.querySelector('audio').play().then(()=>true)");
			waitJs("document.querySelector('audio').currentTime>0.2");
			check(true, "Embedded audio plays");
			screenshot("embedded-music.png");
			section("videos");
			waitJs("document.querySelector('video')?.readyState>=1");
			eval(
					"document.querySelector('video').play().then(()=>{document.querySelector('video').currentTime=2;return"
						+ " true})");
			waitJs("document.querySelector('video').currentTime>=2");
			check(true, "Real flower video plays and seeks inside Swing");
			screenshot("embedded-video.png");
			Files.copy(
					Path.of("docs/embedded-video.png"),
					Path.of("docs/workspace.png"),
					StandardCopyOption.REPLACE_EXISTING);
			eval(
					"[...document.querySelectorAll('#items"
							+ " button')].find(b=>b.textContent.includes('Sintel')).click();true");
			waitJs("document.querySelector('video')?.readyState>=1");
			eval("document.querySelector('video').play().then(()=>true)");
			waitJs("document.querySelector('video').currentTime>0.1");
			check(true, "Sintel clip with sound plays inside Swing");
			section("photos");
			waitJs("document.querySelector('#stage img')?.naturalWidth>0");
			eval(
					"[...document.querySelectorAll('#items"
							+ " button')].find(b=>b.textContent.includes('Apollo')).click();true");
			waitJs("document.querySelector('#stage img')?.naturalWidth===3000");
			check(true, "Original NASA photograph renders inside Swing");
			screenshot("embedded-photos.png");
			section("documents");
			waitJs("document.querySelector('iframe')?.src.endsWith('RFC-9110.pdf')");
			// A real PDF viewer creates an internal PDF extension frame, beyond merely an iframe
			// URL.
			until(() -> browser.getFrameNames().size() > 1);
			check(true, "PDF opens in embedded document frame");
			Thread.sleep(1800);
			screenshot("embedded-pdf.png");
			section("downloads");
			eval(
					"[...document.querySelectorAll('#items"
							+ " button')].find(b=>b.textContent.includes('Tài liệu đọc"
							+ " thêm')).click();document.querySelector('#download').click();true");
			JFileChooser[] chooser = {null};
			until(
					() -> {
						try {
							SwingUtilities.invokeAndWait(
									() -> {
										for (Window w : Window.getWindows())
											if (w.isShowing() && w instanceof JDialog d) {
												JFileChooser c = find(d, JFileChooser.class);
												if (c != null) chooser[0] = c;
											}
									});
							return chooser[0] != null;
						} catch (Exception e) {
							return false;
						}
					});
			Path download = dir.resolve("download.zip");
			SwingUtilities.invokeAndWait(
					() -> {
						chooser[0].setSelectedFile(download.toFile());
						chooser[0].approveSelection();
					});
			until(
					() -> {
						try {
							return Files.size(download)
									== Files.size(
											Path.of("public/media/downloads/Tai-lieu-mau.zip"));
						} catch (Exception e) {
							return false;
						}
					});
			check(
					Arrays.equals(
							Files.readAllBytes(download),
							Files.readAllBytes(Path.of("public/media/downloads/Tai-lieu-mau.zip"))),
					"Embedded download uses save dialog and preserves exact ZIP bytes");
			screenshot("embedded-downloads.png");
			SwingUtilities.invokeAndWait(() -> button(window, "Gửi request").doClick());
			Db db = new Db(config);
			until(
					() -> {
						try {
							return !db.rows("SELECT id FROM history WHERE status LIKE 'HTTP/%'")
									.isEmpty();
						} catch (Exception e) {
							return false;
						}
					});
			check(true, "Manual socket request remains usable");
			java.lang.reflect.Field addressField = EmbeddedBrowser.class.getDeclaredField("address");
			addressField.setAccessible(true);
			String[] displayedAddress = {null};
			SwingUtilities.invokeAndWait(() -> displayedAddress[0] = readAddress(addressField, pane));
			check(java.net.URI.create(displayedAddress[0]).getPort() == -1,
					"Browser address bar hides the server port");

			check(
					db.rows("SELECT id FROM sessions WHERE disconnected_at IS NULL").size() == 1,
					"Browser and socket client share one active server session");
			SwingUtilities.invokeAndWait(() -> button(pane, "HEAD").doClick());
			until(
					() -> {
						try {
							return db.rows("SELECT id FROM requests WHERE method='HEAD'").size()
									>= 2;
						} catch (Exception e) {
							return false;
						}
					});
			SwingUtilities.invokeAndWait(() -> button(pane, "HEAD").doClick());
			until(
					() -> {
						try {
							return db.rows("SELECT id FROM requests WHERE method='HEAD'").size()
									>= 3;
						} catch (Exception e) {
							return false;
						}
					});
			check(true, "Browser toolbar HEAD can run repeatedly");
			SwingUtilities.invokeLater(() -> button(pane, "POST").doClick());
			JDialog[] postDialog = {null};
			until(
					() -> {
						try {
							SwingUtilities.invokeAndWait(
									() -> {
										for (Window w : Window.getWindows())
											if (w instanceof JDialog d
													&& d.isShowing()
													&& d.getTitle().contains("POST"))
												postDialog[0] = d;
									});
							return postDialog[0] != null;
						} catch (Exception e) {
							return false;
						}
					});
			SwingUtilities.invokeAndWait(
					() -> {
						find(postDialog[0], JTextArea.class).setText("Nội dung từ nút POST");
						JOptionPane option = find(postDialog[0], JOptionPane.class);
						option.setValue(JOptionPane.OK_OPTION);
					});
			until(
					() -> {
						try {
							return !db.rows(
											"SELECT id FROM requests WHERE method='POST' AND"
													+ " target='/api/echo'")
									.isEmpty();
						} catch (Exception e) {
							return false;
						}
					});
			check(true, "Browser toolbar POST submits data to the server");
			check(
					!db.rows(
									"SELECT id FROM requests WHERE action='DOWNLOAD' AND"
											+ " method='GET' AND session_id IS NOT NULL")
							.isEmpty(),
					"Embedded download is labeled and associated with the server session");
			check(
					!db.rows("SELECT id FROM history WHERE status='200'").isEmpty(),
					"Embedded navigation persists client history");
			check(
					!db.rows(
									"SELECT id FROM requests WHERE target LIKE '%Moonlight%' OR"
											+ " target LIKE '%Hoa-va-ong%'")
							.isEmpty(),
					"Embedded media uses own server and MySQL request log");
			SwingUtilities.invokeAndWait(() -> button(pane, "←").doClick());
			waitJs("location.pathname==='/documents'");
			check(true, "Back button navigates embedded history");
			SwingUtilities.invokeAndWait(() -> button(pane, "→").doClick());
			waitJs("location.pathname==='/downloads'");
			check(true, "Forward button navigates embedded history");
			CefBrowser previous = browser;
			SwingUtilities.invokeAndWait(
					() -> {
						button(window, "Đăng xuất").doClick();
						window.login.doClick();
					});
			until(
					() -> {
						try {
							SwingUtilities.invokeAndWait(
									() -> pane = find(window, EmbeddedBrowser.class));
							if (pane == null) return false;
							browser = (CefBrowser) field.get(pane);
							return browser != null
									&& browser != previous
									&& browser.getIdentifier() > 0;
						} catch (Exception e) {
							return false;
						}
					});
			waitJs(
					"!!document.querySelector('audio') &&"
							+ " document.querySelector('audio').readyState>=1");
			check(true, "Reconnect creates a fresh embedded browser without restarting the app");
			check(uncaught.isEmpty(), "No uncaught Java UI errors");
			System.out.println("ALL " + checks + " EMBEDDED CHECKS PASSED");
		} finally {
			if (window != null) SwingUtilities.invokeAndWait(() -> window.dispose());
			BrowserRuntime.shutdown();
			for (Process p : IntegrationTest.processes) if (p.isAlive()) IntegrationTest.stop(p);
			TestDatabase.cleanup();
		}
		System.exit(0);
	}
}
