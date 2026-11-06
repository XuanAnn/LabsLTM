package common;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class AntDesignTheme {
	// Official Ant Design 5.x Color Palette
	public static final Color PRIMARY = new Color(0x16, 0x77, 0xFF); // #1677ff Daybreak Blue
	public static final Color PRIMARY_HOVER = new Color(0x40, 0x96, 0xFF); // #4096ff
	public static final Color PRIMARY_ACTIVE = new Color(0x09, 0x58, 0xD9); // #0958d9
	public static final Color PRIMARY_BG = new Color(0xE6, 0xF4, 0xFF); // #e6f4ff

	public static final Color SUCCESS = new Color(0x52, 0xC4, 0x1A); // #52c41a
	public static final Color SUCCESS_BG = new Color(0xF6, 0xFF, 0xED); // #f6ffed
	public static final Color SUCCESS_BORDER = new Color(0xB7, 0xEB, 0x8F); // #b7eb8f

	public static final Color WARNING = new Color(0xFA, 0xAD, 0x14); // #faad14
	public static final Color WARNING_BG = new Color(0xFF, 0xFB, 0xE6); // #fffbe6
	public static final Color WARNING_BORDER = new Color(0xFF, 0xE5, 0x8F); // #ffe58f

	public static final Color ERROR = new Color(0xFF, 0x4D, 0x4F); // #ff4d4f
	public static final Color ERROR_BG = new Color(0xFF, 0xF2, 0xF0); // #fff2f0
	public static final Color ERROR_BORDER = new Color(0xFF, 0xCC, 0xC7); // #ffccc7

	// Neutral tones
	public static final Color TEXT_TITLE =
			new Color(0x00, 0x00, 0x00, (int) (255 * 0.88)); // rgba(0,0,0,0.88)
	public static final Color TEXT_BODY =
			new Color(0x00, 0x00, 0x00, (int) (255 * 0.65)); // rgba(0,0,0,0.65)
	public static final Color TEXT_MUTED =
			new Color(0x00, 0x00, 0x00, (int) (255 * 0.45)); // rgba(0,0,0,0.45)
	public static final Color BORDER = new Color(0xD9, 0xD9, 0xD9); // #d9d9d9
	public static final Color BORDER_SPLIT = new Color(0xF0, 0xF0, 0xF0); // #f0f0f0
	public static final Color BG_LAYOUT = new Color(0xF5, 0xF5, 0xF5); // #f5f5f5
	public static final Color BG_CONTAINER = Color.WHITE;

	public static final Font FONT_TITLE_LARGE = new Font("Segoe UI", Font.BOLD, 18);
	public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
	public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
	public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
	public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);

	public static void setup() {
		try {
			UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
			// Apply FlatLaf Light as modern foundation

			// Ant Design typography & global tokens
			UIManager.put("defaultFont", FONT_REGULAR);
			UIManager.put("Button.arc", 6);
			UIManager.put("Component.arc", 6);
			UIManager.put("TextComponent.arc", 6);
			UIManager.put("ProgressBar.arc", 6);

			// Ant Design Accent
			UIManager.put("Component.focusColor", PRIMARY_BG);
			UIManager.put("Component.focusedBorderColor", PRIMARY);
			UIManager.put("Component.borderColor", BORDER);

			// Table styling
			UIManager.put("Table.rowHeight", 34);
			UIManager.put("Table.showHorizontalLines", true);
			UIManager.put("Table.showVerticalLines", false);
			UIManager.put("Table.gridColor", BORDER_SPLIT);
			UIManager.put("Table.selectionBackground", PRIMARY_BG);
			UIManager.put("Table.selectionForeground", TEXT_TITLE);
			UIManager.put("TableHeader.background", new Color(0xFA, 0xFA, 0xFA));
			UIManager.put("TableHeader.foreground", TEXT_TITLE);
			UIManager.put("TableHeader.font", FONT_BOLD);

			// TabbedPane
			UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
			UIManager.put("TabbedPane.selectedForeground", PRIMARY);
			UIManager.put("TabbedPane.underlineColor", PRIMARY);
			UIManager.put("TabbedPane.hoverColor", PRIMARY_BG);

		} catch (Throwable exception) {
			System.err.println("Could not load FlatLaf: " + exception.getMessage());
		}
	}

	public static JButton createPrimaryButton(String text) {
		JButton btn = new JButton(text);
		btn.setFont(FONT_BOLD);
		btn.setBackground(PRIMARY);
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		btn.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(PRIMARY, 1, true),
						new EmptyBorder(6, 14, 6, 14)));
		return btn;
	}

	public static JButton createDefaultButton(String text) {
		JButton btn = new JButton(text);
		btn.setFont(FONT_REGULAR);
		btn.setBackground(Color.WHITE);
		btn.setForeground(TEXT_TITLE);
		btn.setFocusPainted(false);
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		btn.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(BORDER, 1, true),
						new EmptyBorder(6, 14, 6, 14)));
		return btn;
	}

	public static JButton createDangerButton(String text) {
		JButton btn = new JButton(text);
		btn.setFont(FONT_BOLD);
		btn.setBackground(ERROR);
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		btn.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(ERROR, 1, true),
						new EmptyBorder(6, 14, 6, 14)));
		return btn;
	}

	public static JPanel createCard(String title) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(BG_CONTAINER);
		card.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(BORDER, 1, true),
						new EmptyBorder(12, 16, 12, 16)));
		if (title != null && !title.isEmpty()) {
			JLabel lbl = new JLabel(title);
			lbl.setFont(FONT_TITLE);
			lbl.setForeground(TEXT_TITLE);
			lbl.setBorder(new EmptyBorder(0, 0, 10, 0));
			card.add(lbl, BorderLayout.NORTH);
		}
		return card;
	}

	public static JLabel createTag(String text, Color bg, Color textCol, Color borderCol) {
		JLabel tag = new JLabel(text, SwingConstants.CENTER);
		tag.setFont(FONT_SMALL);
		tag.setOpaque(true);
		tag.setBackground(bg);
		tag.setForeground(textCol);
		tag.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(borderCol, 1, true),
						new EmptyBorder(2, 8, 2, 8)));
		return tag;
	}
}
