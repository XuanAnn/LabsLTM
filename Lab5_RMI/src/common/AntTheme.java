package common;

import client.*;
import server.*;
import common.*;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.*;

/** Visual tokens copied locally from the user's Lab 1; this lab is self-contained. */
public final class AntTheme {
	public static final Color BLUE = AntDesignTheme.PRIMARY,
			TEXT = AntDesignTheme.TEXT_TITLE,
			MUTED = AntDesignTheme.TEXT_MUTED,
			BORDER = AntDesignTheme.BORDER,
			BG = AntDesignTheme.BG_LAYOUT,
			PALE = AntDesignTheme.PRIMARY_BG;

	public static void install() {
		AntDesignTheme.setup();
		for (Object key : java.util.Collections.list(UIManager.getDefaults().keys())) {
			if (key.toString().endsWith(".font")) {
				UIManager.put(key, AntDesignTheme.FONT_REGULAR);
			}
		}
		UIManager.put("Panel.background", Color.WHITE);
	}

	public static JTextField input(String value, boolean ignored) {
		JTextField jTextField = new JTextField(value);
		jTextField.setFont(AntDesignTheme.FONT_REGULAR);
		jTextField.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(BORDER),
						BorderFactory.createEmptyBorder(7, 9, 7, 9)));
		return jTextField;
	}

	public static JButton button(String text, boolean primary) {
		return primary
				? AntDesignTheme.createPrimaryButton(text)
				: AntDesignTheme.createDefaultButton(text);
	}

	public static JLabel label(String text, int size, boolean bold, Color color) {
		JLabel jLabel = new JLabel(text);
		jLabel.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
		jLabel.setForeground(color);
		return jLabel;
	}

	public static JPanel box(int gap) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));
		return panel;
	}

	public static JScrollPane scroll(Component component) {
		JScrollPane jScrollPane = new JScrollPane(component);
		jScrollPane.setBorder(BorderFactory.createEmptyBorder());
		jScrollPane.getVerticalScrollBar().setUnitIncrement(18);
		return jScrollPane;
	}

	public static final class RoundedBorder extends LineBorder {
		public RoundedBorder(Color color, int radius) {
			super(color, 1, true);
		}
	}
}
