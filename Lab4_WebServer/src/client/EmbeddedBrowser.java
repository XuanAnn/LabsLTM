package client;

import client.*;
import server.*;
import common.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import common.*;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import javax.swing.*;

/** Phần thư viện browser nằm ở đây; ClientUI chỉ gọi các thao tác đơn giản. */
public final class EmbeddedBrowser extends JPanel implements AutoCloseable {
	private final JTextField address = DesktopUi.field("", 38);
	private final JButton back = DesktopUi.button("←", false);
	private final JButton forward = DesktopUi.button("→", false);
	private final JButton reload = DesktopUi.button("Tải lại", false);
	private final JLabel state = new JLabel("Đang mở trình duyệt…");
	private final JPanel surface = new JPanel(new BorderLayout());
	private final JTextArea response = new JTextArea(6, 50);
	private final JPanel responsePanel = new JPanel(new BorderLayout(4, 4));
	private final Set<Integer> completed = ConcurrentHashMap.newKeySet();
	private final String home;
	private final BiConsumer<String, Integer> visit;
	private final ClientController controller;
	private volatile CefBrowser browser;
	private CefClient client;
	private String pending;
	private volatile boolean closed;

	public EmbeddedBrowser(
			String home, BiConsumer<String, Integer> visit, ClientController controller) {
		super(new BorderLayout(0, 8));
		this.home = home;
		this.pending = home;
		this.visit = visit;
		this.controller = controller;
		address.setText(BrowserAddress.display(home));
		back.setEnabled(false);
		forward.setEnabled(false);
		reload.setEnabled(false);
		add(createToolbar(), BorderLayout.NORTH);
		surface.setBackground(Color.WHITE);
		surface.add(new JLabel("Đang chuẩn bị nội dung…", SwingConstants.CENTER));
		add(surface, BorderLayout.CENTER);
		add(createFooter(), BorderLayout.SOUTH);
		addHierarchyListener(event -> updateVisibility(event));
		initializeBrowser();
	}

	private JPanel createToolbar() {
		JButton openButton = DesktopUi.button("Mở", true);
		JButton homeButton = DesktopUi.button("Trang chủ", false);
		JButton headButton = DesktopUi.button("HEAD", false);
		JButton postButton = DesktopUi.button("POST", false);
		JButton downloadButton = DesktopUi.button("Tải file", false);
		JPanel toolbar = new JPanel(new BorderLayout(8, 0));
		toolbar.setOpaque(false);
		toolbar.add(DesktopUi.row(back, forward, reload, homeButton), BorderLayout.WEST);
		toolbar.add(address, BorderLayout.CENTER);
		toolbar.add(
				DesktopUi.row(openButton, headButton, postButton, downloadButton),
				BorderLayout.EAST);
		openButton.addActionListener(event -> navigate(address.getText()));
		address.addActionListener(event -> navigate(address.getText()));
		homeButton.addActionListener(event -> navigate(home));
		back.addActionListener(event -> goBack());
		forward.addActionListener(event -> goForward());
		reload.addActionListener(event -> reloadPage());
		headButton.addActionListener(event -> send(headButton, BrowserAddress.resolve(address.getText(), pending), "HEAD", ""));
		postButton.addActionListener(event -> showPostDialog(postButton));
		downloadButton.addActionListener(event -> startSelectedDownload());
		return toolbar;
	}

	private JPanel createFooter() {
		response.setEditable(false);
		response.setFont(new Font("Consolas", Font.PLAIN, 12));
		JButton closeResult = DesktopUi.button("Đóng kết quả", false);
		closeResult.addActionListener(event -> hideResponse());
		responsePanel.add(new JScrollPane(response), BorderLayout.CENTER);
		responsePanel.add(closeResult, BorderLayout.EAST);
		responsePanel.setVisible(false);
		JPanel footer = new JPanel(new BorderLayout(4, 4));
		footer.add(state, BorderLayout.NORTH);
		footer.add(responsePanel, BorderLayout.CENTER);
		return footer;
	}

	private void initializeBrowser() {
		// Tải/khởi tạo thư viện trên luồng nền, không làm đứng giao diện.
		BrowserRuntime.get(this::showProgress)
				.whenComplete(
						(app, error) -> {
							SwingUtilities.invokeLater(() -> finishInitialization(app, error));
						});
	}

	private void finishInitialization(CefApp app, Throwable error) {
		if (closed) {
			return;
		}
		if (error != null) {
			showInitializationError(error);
			return;
		}
		client = app.createClient();
		registerBrowserEvents();
		browser = client.createBrowser(pending, false, false);
		surface.removeAll();
		surface.add(browser.getUIComponent(), BorderLayout.CENTER);
		surface.revalidate();
		surface.repaint();
	}

	private void showInitializationError(Throwable error) {
		state.setText("Không mở được trình duyệt. Kiểm tra kết nối rồi mở lại ứng dụng.");
		JTextArea details =
				new JTextArea("Không chuẩn bị được bộ hiển thị.\n" + error.getMessage());
		details.setEditable(false);
		details.setLineWrap(true);
		surface.removeAll();
		surface.add(new JScrollPane(details), BorderLayout.CENTER);
		surface.revalidate();
	}

	private void registerBrowserEvents() {
		registerSessionHeader();
		registerPageEvents();
		registerWindowEvents();
		registerDownloadEvents();
	}

	private void registerSessionHeader() {
		client.addRequestHandler(
				new CefRequestHandlerAdapter() {
					@Override
					public CefResourceRequestHandler getResourceRequestHandler(
							CefBrowser active,
							CefFrame frame,
							CefRequest request,
							boolean navigation,
							boolean download,
							String initiator,
							BoolRef disable) {
						return new CefResourceRequestHandlerAdapter() {
							@Override
							public boolean onBeforeResourceLoad(
									CefBrowser active, CefFrame frame, CefRequest request) {
								addSessionHeader(request);
								return false;
							}
						};
					}
				});
	}

	private void addSessionHeader(CefRequest request) {
		Map<String, String> headers = controller.headersFor(request.getURL());
		for (Map.Entry<String, String> header : headers.entrySet()) {
			request.setHeaderByName(header.getKey(), header.getValue(), true);
		}
	}

	private void registerPageEvents() {
		client.addDisplayHandler(
				new CefDisplayHandlerAdapter() {
					@Override
					public void onAddressChange(CefBrowser active, CefFrame frame, String url) {
						if (frame.isMain()) {
							SwingUtilities.invokeLater(() -> showAddress(url));
						}
					}
				});
		client.addLoadHandler(
				new CefLoadHandlerAdapter() {
					@Override
					public void onLoadingStateChange(
							CefBrowser active,
							boolean loading,
							boolean canBack,
							boolean canForward) {
						SwingUtilities.invokeLater(
								() -> showLoadingState(loading, canBack, canForward));
					}

					@Override
					public void onLoadEnd(CefBrowser active, CefFrame frame, int status) {
						if (frame.isMain()) {
							visit.accept(active.getURL(), status);
						}
					}

					@Override
					public void onLoadError(
							CefBrowser active,
							CefFrame frame,
							CefLoadHandler.ErrorCode code,
							String message,
							String failedUrl) {
						if (frame.isMain() && code != CefLoadHandler.ErrorCode.ERR_ABORTED) {
							showProgress("Không tải được trang: " + message);
						}
					}
				});
	}

	private void registerWindowEvents() {
		client.addLifeSpanHandler(
				new CefLifeSpanHandlerAdapter() {
					@Override
					public void onAfterCreated(CefBrowser active) {
						SwingUtilities.invokeLater(() -> showBrowserWindow(active));
					}

					@Override
					public boolean onBeforePopup(
							CefBrowser active, CefFrame frame, String target, String name) {
						SwingUtilities.invokeLater(() -> navigate(target));
						return true;
					}
				});
	}

	private void registerDownloadEvents() {
		client.addDownloadHandler(
				new CefDownloadHandlerAdapter() {
					@Override
					public boolean onBeforeDownload(
							CefBrowser active,
							CefDownloadItem item,
							String suggested,
							CefBeforeDownloadCallback callback) {
						String filename = suggested;
						if (filename.isBlank()) {
							filename = "download";
						}
						String safeName = Path.of(filename).getFileName().toString();
						SwingUtilities.invokeLater(
								() -> chooseDownloadDestination(safeName, callback));
						return true;
					}

					@Override
					public void onDownloadUpdated(
							CefBrowser active,
							CefDownloadItem item,
							CefDownloadItemCallback callback) {
						updateDownload(item);
					}
				});
	}

	private void chooseDownloadDestination(String filename, CefBeforeDownloadCallback callback) {
		if (closed) {
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File(filename));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		Path file = chooser.getSelectedFile().toPath();
		if (Files.exists(file)) {
			int answer =
					JOptionPane.showConfirmDialog(
							this, "Ghi đè file đã có?", "Tải file", JOptionPane.YES_NO_OPTION);
			if (answer != JOptionPane.YES_OPTION) {
				return;
			}
		}
		callback.Continue(file.toAbsolutePath().toString(), false);
	}

	private void updateDownload(CefDownloadItem item) {
		// Đọc dữ liệu ngay trong callback. Không chuyển đối tượng native item sang luồng EDT.
		if (item.isComplete() && completed.add(item.getId())) {
			String fullPath = item.getFullPath();
			String filename = "file";
			if (fullPath != null) {
				filename = Path.of(fullPath).getFileName().toString();
			}
			visit.accept(item.getURL(), 200);
			showProgress("Đã tải: " + filename);
		} else if (item.isInProgress()) {
			showProgress("Đang tải file · " + Math.max(0, item.getPercentComplete()) + "%");
		} else if (item.isCanceled()) {
			showProgress("Đã hủy tải file");
		}
	}

	private void startSelectedDownload() {
		CefBrowser active = browser;
		if (active == null) {
			return;
		}
		String parameters =
				"{\"expression\":\"document.querySelector('#download:not([hidden])')?.href ||"
					+ " location.href\",\"returnByValue\":true}";
		active.getDevToolsClient()
				.executeDevToolsMethod("Runtime.evaluate", parameters)
				.whenComplete((result, error) -> handleSelectedDownload(active, result, error));
	}

	private void handleSelectedDownload(CefBrowser active, String result, Throwable error) {
		if (error != null) {
			showProgress("Không tải được file: " + error.getMessage());
			return;
		}
		try {
			JsonObject message = JsonParser.parseString(result).getAsJsonObject();
			String target = message.getAsJsonObject("result").get("value").getAsString();
			SwingUtilities.invokeLater(() -> downloadTarget(active, target));
		} catch (Exception exception) {
			showProgress("Không tải được file: " + exception.getMessage());
		}
	}

	private void downloadTarget(CefBrowser active, String target) {
		if (!closed) {
			active.startDownload(ClientController.downloadUrl(target));
		}
	}

	private void showPostDialog(JButton postButton) {
		String endpointAddress = java.net.URI.create(home).resolve("/api/echo").toString();
		JTextField endpoint = DesktopUi.field(endpointAddress, 40);
		JTextArea payload = new JTextArea("Xin chào", 5, 45);
		JPanel form = new JPanel(new BorderLayout(8, 8));
		form.add(endpoint, BorderLayout.NORTH);
		form.add(new JScrollPane(payload), BorderLayout.CENTER);
		if (JOptionPane.showConfirmDialog(
						this, form, "Gửi dữ liệu (POST)", JOptionPane.OK_CANCEL_OPTION)
				== JOptionPane.OK_OPTION) {
			send(postButton, endpoint.getText(), "POST", payload.getText());
		}
	}

	private void send(JButton button, String location, String method, String body) {
		DesktopUi.work(
				this,
				button,
				() -> {
					WebClient.Response result = controller.request(location, method, body);
					SwingUtilities.invokeLater(() -> showResponse(method, result));
				});
	}

	private void showResponse(String method, WebClient.Response result) {
		StringBuilder text = new StringBuilder(result.status());
		for (Map.Entry<String, String> header : result.headers().entrySet()) {
			text.append("\n").append(header.getKey()).append(": ").append(header.getValue());
		}
		text.append("\n\n").append(new String(result.body(), StandardCharsets.UTF_8));
		state.setText(method + " · " + result.status());
		response.setText(text.toString());
		response.setCaretPosition(0);
		responsePanel.setVisible(true);
		revalidate();
	}

	private void hideResponse() {
		responsePanel.setVisible(false);
		revalidate();
	}

	private void showAddress(String url) {
		if (!closed) {
			pending = url;
			address.setText(BrowserAddress.display(url));
		}
	}

	private void showLoadingState(boolean loading, boolean canBack, boolean canForward) {
		if (closed) {
			return;
		}
		back.setEnabled(canBack);
		forward.setEnabled(canForward);
		reload.setEnabled(true);
		if (loading) {
			state.setText("Đang tải…");
		} else {
			state.setText("Sẵn sàng");
		}
	}

	private void showProgress(String message) {
		SwingUtilities.invokeLater(
				() -> {
					if (!closed) {
						state.setText(message);
					}
				});
	}

	private void showBrowserWindow(CefBrowser active) {
		if (!closed) {
			active.setWindowVisibility(isShowing());
			active.getUIComponent().repaint();
		}
	}

	private void updateVisibility(HierarchyEvent event) {
		CefBrowser active = browser;
		if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
				&& active != null
				&& active.getIdentifier() > 0) {
			active.setWindowVisibility(isShowing());
		}
	}

	private void goBack() {
		if (browser != null) {
			browser.goBack();
		}
	}

	private void goForward() {
		if (browser != null) {
			browser.goForward();
		}
	}

	private void reloadPage() {
		if (browser != null) {
			browser.reloadIgnoreCache();
		}
	}

	public void navigate(String location) {
		String target = BrowserAddress.resolve(location, pending);
		pending = target;
		address.setText(BrowserAddress.display(target));
		if (browser != null) {
			browser.loadURL(target);
		}
	}

	@Override
	public void close() {
		closed = true;
		if (browser != null) {
			browser.close(true);
			browser = null;
		}
		if (client != null) {
			client.dispose();
			client = null;
		}
	}
}
