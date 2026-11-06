package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class ClientUI {
	private final ClientWindow ui = new ClientWindow("Quản lý tệp", 2121, 0, true);
	private final ClientController controller = new ClientController();
	private final JTextField local =
			DesktopUi.field(Path.of(".").toAbsolutePath().normalize().toString(), 25);
	private final JTextField remote = DesktopUi.field("/", 22);
	private final DefaultTableModel localModel = DesktopUi.model("Tên file", "Loại", "Kích thước");
	private final DefaultTableModel remoteModel = DesktopUi.model("Tên file", "Loại", "Kích thước");
	private final JTable localTable = DesktopUi.table(localModel);
	private final JTable remoteTable = DesktopUi.table(remoteModel);
	private final JTextArea log = new JTextArea();
	private final JButton browseButton = DesktopUi.button("Chọn thư mục", false);
	private final JButton openButton = DesktopUi.button("Mở", true);
	private final JButton upButton = DesktopUi.button("Lên", false);
	private final JButton uploadButton = DesktopUi.button("Upload →", true);
	private final JButton downloadButton = DesktopUi.button("← Download", true);
	private final JButton createFolderButton = DesktopUi.button("Tạo thư mục", false);
	private final JButton deleteButton = DesktopUi.button("Xóa trên server", false);
	private final JButton refreshButton = DesktopUi.button("Làm mới", false);
	private List<ClientController.Entry> entries = new ArrayList<>();

	private ClientUI() {
		ui.user.setText("student");
		ui.password.setText("student123");
		registerEvents();
		ui.setVisible(true);
	}

	private void registerEvents() {
		ui.login.addActionListener(event -> login());
		ui.register.addActionListener(event -> registerAccount());
		browseButton.addActionListener(event -> chooseLocalFolder());
		local.addActionListener(event -> refreshLocal());
		openButton.addActionListener(event -> refreshRemote());
		upButton.addActionListener(event -> openParentFolder());
		refreshButton.addActionListener(event -> refreshBothPanels());
		uploadButton.addActionListener(event -> uploadSelectedFile());
		downloadButton.addActionListener(event -> downloadSelectedFile());
		createFolderButton.addActionListener(event -> createRemoteFolder());
		deleteButton.addActionListener(event -> deleteRemoteEntry());
		localTable.addMouseListener(
				new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent event) {
						if (event.getClickCount() == 2) {
							openLocalFolder();
						}
					}
				});
		remoteTable.addMouseListener(
				new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent event) {
						if (event.getClickCount() == 2) {
							openRemoteFolder();
						}
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
		ui.open(
				workspace(),
				settings.user(),
				settings.host() + ":" + settings.port(),
				controller.endpoint(),
				controller::close);
		refreshBothPanels();
	}

	private void registerAccount() {
		ClientWindow.Login settings = ui.values();
		ui.action(ui.register, () -> ui.notice(controller.register(settings) + " — bấm Đăng nhập"));
	}

	private JPanel workspace() {
		JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBackground(AntTheme.BG);
		JPanel localPanel = DesktopUi.card("Máy của bạn");
		localPanel.add(DesktopUi.row(local, browseButton), BorderLayout.NORTH);
		localPanel.add(new JScrollPane(localTable), BorderLayout.CENTER);
		JPanel remotePanel = DesktopUi.card("FTP Server");
		remotePanel.add(DesktopUi.row(remote, openButton, upButton), BorderLayout.NORTH);
		remotePanel.add(new JScrollPane(remoteTable), BorderLayout.CENTER);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPanel, remotePanel);
		split.setResizeWeight(0.5);
		root.add(split, BorderLayout.CENTER);
		root.add(createBottomPanel(), BorderLayout.SOUTH);
		return root;
	}

	private JPanel createBottomPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(
				DesktopUi.row(
						uploadButton,
						downloadButton,
						createFolderButton,
						deleteButton,
						refreshButton),
				BorderLayout.NORTH);
		log.setEditable(false);
		log.setFont(new Font("Consolas", Font.PLAIN, 12));
		JScrollPane console = new JScrollPane(log);
		console.setPreferredSize(new Dimension(500, 120));
		panel.add(console, BorderLayout.CENTER);
		return panel;
	}

	private void chooseLocalFolder() {
		JFileChooser chooser = new JFileChooser(local.getText());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(ui) == JFileChooser.APPROVE_OPTION) {
			local.setText(chooser.getSelectedFile().toString());
			refreshLocal();
		}
	}

	private void openLocalFolder() {
		int row = localTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		Path file = Path.of(local.getText()).resolve(String.valueOf(localTable.getValueAt(row, 0)));
		if (Files.isDirectory(file)) {
			local.setText(file.toString());
			refreshLocal();
		}
	}

	private void openRemoteFolder() {
		ClientController.Entry entry = selected();
		if (entry != null && entry.directory()) {
			remote.setText(child(remote.getText(), entry.name()));
			refreshRemote();
		}
	}

	private void openParentFolder() {
		String directory = remote.getText();
		int separator = directory.lastIndexOf('/');
		if (separator <= 0) {
			remote.setText("/");
		} else {
			remote.setText(directory.substring(0, separator));
		}
		refreshRemote();
	}

	private void uploadSelectedFile() {
		int row = localTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		Path file = Path.of(local.getText()).resolve(String.valueOf(localTable.getValueAt(row, 0)));
		String directory = remote.getText();
		ui.action(uploadButton, () -> uploadFile(directory, file));
	}

	private void uploadFile(String directory, Path file) throws Exception {
		if (!Files.isRegularFile(file)) {
			throw new Exception("Chọn một file");
		}
		controller.upload(directory, file);
		DesktopUi.log(log, "Upload thành công: " + file.getFileName());
		refreshRemote();
	}

	private void downloadSelectedFile() {
		ClientController.Entry entry = selected();
		if (entry == null || entry.directory()) {
			return;
		}
		Path destination = Path.of(local.getText()).resolve(entry.name());
		String directory = remote.getText();
		ui.action(
				downloadButton,
				() -> {
					controller.download(directory, entry.name(), destination);
					DesktopUi.log(log, "Download thành công: " + destination);
					SwingUtilities.invokeLater(this::refreshLocal);
				});
	}

	private void createRemoteFolder() {
		String name = JOptionPane.showInputDialog(ui, "Tên thư mục mới");
		if (name == null || name.isBlank()) {
			return;
		}
		String directory = remote.getText();
		ui.action(
				createFolderButton,
				() -> {
					controller.mkdir(directory, name);
					refreshRemote();
				});
	}

	private void deleteRemoteEntry() {
		ClientController.Entry entry = selected();
		if (entry == null) {
			return;
		}
		int answer =
				JOptionPane.showConfirmDialog(
						ui, "Xóa " + entry.name() + "?", "FTP", JOptionPane.YES_NO_OPTION);
		if (answer != JOptionPane.YES_OPTION) {
			return;
		}
		String directory = remote.getText();
		ui.action(
				deleteButton,
				() -> {
					controller.delete(directory, entry);
					refreshRemote();
				});
	}

	private void refreshBothPanels() {
		refreshLocal();
		refreshRemote();
	}

	private void refreshLocal() {
		String directory = DesktopUi.value(local);
		ui.action(null, () -> loadLocalFiles(directory));
	}

	private void loadLocalFiles(String directory) throws Exception {
		List<Path> paths = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(Path.of(directory))) {
			for (Path file : files) {
				paths.add(file);
			}
		}
		Collections.sort(paths);
		List<Object[]> rows = new ArrayList<>();
		for (Path file : paths) {
			String type = "File";
			long size;
			if (Files.isDirectory(file)) {
				type = "Folder";
				size = 0;
			} else {
				size = Files.size(file);
			}
			rows.add(new Object[] {file.getFileName(), type, size});
		}
		DesktopUi.rows(localModel, rows);
	}

	private void refreshRemote() {
		String directory = DesktopUi.value(remote);
		ui.action(
				null,
				() -> {
					List<ClientController.Entry> result = controller.list(directory);
					SwingUtilities.invokeLater(() -> showRemoteFiles(result));
				});
	}

	private void showRemoteFiles(List<ClientController.Entry> result) {
		entries = result;
		remoteModel.setRowCount(0);
		for (ClientController.Entry entry : result) {
			String type = "File";
			if (entry.directory()) {
				type = "Folder";
			}
			remoteModel.addRow(new Object[] {entry.name(), type, entry.size()});
		}
	}

	private ClientController.Entry selected() {
		int row = remoteTable.getSelectedRow();
		if (row < 0) {
			return null;
		}
		return entries.get(remoteTable.convertRowIndexToModel(row));
	}

	private static String child(String parent, String name) {
		if (parent.endsWith("/")) {
			return parent + name;
		}
		return parent + "/" + name;
	}

	public static void main(String[] arguments) {
		DesktopUi.init();
		SwingUtilities.invokeLater(ClientUI::new);
	}
}
