package common;

import client.*;
import server.*;
import common.*;

import java.awt.*;
import java.util.*;

import javax.swing.*;

/** Small Swing workbench; network operations always run off the event thread. */
public final class Desk {
	public final JFrame frame;
	public final JPanel fields = new JPanel(new GridBagLayout());
	public final JPanel actions = new JPanel(new GridLayout(0, 4, 6, 6));
	public final JTextArea output = new JTextArea();
	private final Map<String, JTextField> inputs = new LinkedHashMap<>();
	private int row;

	public interface Task {
		String run() throws Exception;
	}

	public Desk(String title) {
		AntTheme.install();
		frame = new JFrame(title);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(1120, 780);
		frame.setMinimumSize(new Dimension(920, 650));
		frame.setLocationByPlatform(true);
		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
		header.add(AntTheme.label(title, 23, true, AntTheme.TEXT), BorderLayout.WEST);
		header.add(
				AntTheme.label("JAVA 21  /  LẬP TRÌNH MẠNG", 11, true, AntTheme.MUTED),
				BorderLayout.EAST);
		JPanel content = new JPanel(new BorderLayout(20, 20));
		content.setBackground(AntTheme.BG);
		content.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
		JPanel config = new JPanel(new BorderLayout(0, 14));
		config.setBorder(
				BorderFactory.createCompoundBorder(
						new AntTheme.RoundedBorder(AntTheme.BORDER, 12),
						BorderFactory.createEmptyBorder(18, 18, 18, 18)));
		config.add(AntTheme.label("Cấu hình kết nối", 16, true, AntTheme.TEXT), BorderLayout.NORTH);
		JPanel aligned = new JPanel(new BorderLayout());
		aligned.add(fields, BorderLayout.NORTH);
		config.add(AntTheme.scroll(aligned), BorderLayout.CENTER);
		config.setPreferredSize(new Dimension(310, 500));
		JPanel workspace = new JPanel(new BorderLayout(0, 16));
		workspace.setBorder(
				BorderFactory.createCompoundBorder(
						new AntTheme.RoundedBorder(AntTheme.BORDER, 12),
						BorderFactory.createEmptyBorder(18, 18, 18, 18)));
		JPanel commands = new JPanel(new BorderLayout(0, 14));
		commands.add(AntTheme.label("Thao tác", 16, true, AntTheme.TEXT), BorderLayout.NORTH);
		commands.add(actions, BorderLayout.CENTER);
		workspace.add(commands, BorderLayout.NORTH);
		output.setEditable(false);
		output.setFont(new Font("Consolas", Font.PLAIN, 14));
		output.setLineWrap(true);
		output.setWrapStyleWord(true);
		output.setMargin(new Insets(14, 10, 14, 10));
		workspace.add(AntTheme.scroll(output), BorderLayout.CENTER);
		workspace.add(
				AntTheme.label(
						"Kết quả từ server • Kết nối mạng chạy ngoài luồng giao diện",
						12,
						false,
						AntTheme.MUTED),
				BorderLayout.SOUTH);
		content.add(config, BorderLayout.WEST);
		content.add(workspace, BorderLayout.CENTER);
		frame.add(header, BorderLayout.NORTH);
		frame.add(content, BorderLayout.CENTER);
	}

	public void field(String name, String value) {
		field(name, value, false);
	}

	public void field(String name, String value, boolean secret) {
		JTextField jTextField = AntTheme.input(value, secret);
		inputs.put(name, jTextField);
		GridBagConstraints gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = row++;
		gridBagConstraints.weightx = 1;
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new Insets(0, 0, 6, 0);
		fields.add(AntTheme.label(name, 12, false, AntTheme.MUTED), gridBagConstraints);
		gridBagConstraints.gridy = row++;
		gridBagConstraints.insets = new Insets(0, 0, 16, 0);
		fields.add(jTextField, gridBagConstraints);
	}

	public String get(String name) {
		if (SwingUtilities.isEventDispatchThread()) {
			return inputs.get(name).getText();
		}
		final String[] parts = new String[1];
		try {
			SwingUtilities.invokeAndWait(() -> parts[0] = inputs.get(name).getText());
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		return parts[0];
	}

	public void log(String text) {
		SwingUtilities.invokeLater(
				() -> {
					output.append(text + "\n");
					output.setCaretPosition(output.getDocument().getLength());
				});
	}

	public void button(String text, Task task) {
		JButton button = AntTheme.button(text, actions.getComponentCount() == 0);
		actions.add(button);
		button.addActionListener(
				e -> {
					button.setEnabled(false);
					Thread.startVirtualThread(
							() -> {
								try {
									String result = task.run();
									if (result != null) {
										log(result);
									}
								} catch (Exception x) {
									log("ERROR: " + x.getMessage());
								} finally {
									SwingUtilities.invokeLater(() -> button.setEnabled(true));
								}
							});
				});
	}

	public void show() {
		frame.setVisible(true);
	}
}
