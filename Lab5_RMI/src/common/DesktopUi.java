package common;

import client.*;
import server.*;
import common.*;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

public final class DesktopUi {
	private DesktopUi() {}

	public interface Action {
		void run() throws Exception;
	}

	public static void init() {
		AntTheme.install();
	}

	public static JPanel card(String title) {
		return AntDesignTheme.createCard(title);
	}

	public static JPanel row(Component... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		panel.setOpaque(false);
		for (Component component : components) {
			panel.add(component);
		}
		return panel;
	}

	public static JTextField field(String value, int columns) {
		JTextField jTextField = AntTheme.input(value, false);
		jTextField.setColumns(columns);
		return jTextField;
	}

	public static JButton button(String text, boolean primary) {
		return AntTheme.button(text, primary);
	}

	public static DefaultTableModel model(String... names) {
		return new DefaultTableModel(names, 0) {
			public boolean isCellEditable(int count, int count2) {
				return false;
			}
		};
	}

	public static JTable table(DefaultTableModel model) {
		JTable jTable = new JTable(model);
		jTable.setRowHeight(34);
		jTable.setShowVerticalLines(false);
		jTable.setGridColor(AntDesignTheme.BORDER_SPLIT);
		jTable.setSelectionBackground(AntTheme.PALE);
		jTable.setSelectionForeground(AntTheme.TEXT);
		jTable.getTableHeader().setFont(AntDesignTheme.FONT_BOLD);
		jTable.setAutoCreateRowSorter(true);
		for (int i = 0; i < model.getColumnCount(); i++) {
			if (model.getColumnName(i).toLowerCase(java.util.Locale.ROOT).contains("ip")) {
				jTable.getColumnModel()
						.getColumn(i)
						.setCellRenderer(
								new DefaultTableCellRenderer() {
									public Component getTableCellRendererComponent(
											JTable table,
											Object value,
											boolean selected,
											boolean focus,
											int row,
											int column) {
										return super.getTableCellRendererComponent(
												table, ipOnly(value), selected, focus, row, column);
									}
								});
			}
		}
		return jTable;
	}

	public static void rows(DefaultTableModel model, List<Object[]> rows) {
		SwingUtilities.invokeLater(
				() -> {
					model.setRowCount(0);
					for (Object[] r : rows) {
						model.addRow(r);
					}
				});
	}

	public static void work(Component owner, JButton button, Action action) {
		if (button != null) {
			button.setEnabled(false);
		}
		Thread.startVirtualThread(
				() -> {
					try {
						action.run();
					} catch (Exception exception) {
						SwingUtilities.invokeLater(
								() ->
										JOptionPane.showMessageDialog(
												owner,
												exception.getMessage(),
												"Không thực hiện được",
												JOptionPane.ERROR_MESSAGE));
					} finally {
						if (button != null) {
							SwingUtilities.invokeLater(() -> button.setEnabled(true));
						}
					}
				});
	}

	public static String value(JTextField jTextField) {
		if (SwingUtilities.isEventDispatchThread()) {
			return jTextField.getText();
		}
		String[] parts = new String[1];
		try {
			SwingUtilities.invokeAndWait(() -> parts[0] = jTextField.getText());
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		return parts[0];
	}

	public static JPanel form(String[] labels, JComponent[] fields) {
		JPanel panel = new JPanel(new GridBagLayout());
		for (int i = 0; i < labels.length; i++) {
			GridBagConstraints gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = i;
			gridBagConstraints.anchor = GridBagConstraints.WEST;
			gridBagConstraints.insets = new Insets(5, 0, 5, 14);
			panel.add(new JLabel(labels[i]), gridBagConstraints);
			gridBagConstraints.gridx = 1;
			gridBagConstraints.weightx = 1;
			gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
			panel.add(fields[i], gridBagConstraints);
		}
		return panel;
	}

	public static JPanel header(String title, String subtitle, JComponent actions) {
		JPanel panel = new JPanel(new BorderLayout(16, 0));
		panel.setBackground(Color.WHITE);
		panel.setBorder(new EmptyBorder(16, 20, 16, 20));
		JPanel words = new JPanel(new GridLayout(2, 1, 0, 6));
		words.add(AntTheme.label(title, 21, true, AntTheme.TEXT));
		words.add(AntTheme.label(subtitle, 12, false, AntTheme.MUTED));
		panel.add(words, BorderLayout.CENTER);
		if (actions != null) {
			panel.add(actions, BorderLayout.EAST);
		}
		return panel;
	}

	public static void window(JFrame frame) {
		frame.setSize(1160, 780);
		frame.setMinimumSize(new Dimension(980, 680));
		frame.setLocationByPlatform(true);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.getContentPane().setBackground(AntTheme.BG);
	}

	public static JLabel tag(String text, boolean success) {
		return AntDesignTheme.createTag(
				text,
				success ? AntDesignTheme.SUCCESS_BG : AntDesignTheme.ERROR_BG,
				success ? AntDesignTheme.SUCCESS : AntDesignTheme.ERROR,
				success ? AntDesignTheme.SUCCESS_BORDER : AntDesignTheme.ERROR_BORDER);
	}

	public static String ipOnly(Object value) {
		String text = value == null ? "" : value.toString().replaceFirst("^/", "");
		if (text.startsWith("[") && text.contains("]")) {
			return text.substring(1, text.indexOf(']'));
		}
		if (text.matches("[^:]+:[0-9]+")) {
			return text.substring(0, text.lastIndexOf(':'));
		}
		return text;
	}

	public static void log(JTextArea area, String value) {
		SwingUtilities.invokeLater(
				() -> {
					area.append(java.time.LocalTime.now().withNano(0) + "  " + value + "\n");
					area.setCaretPosition(area.getDocument().getLength());
				});
	}
}
