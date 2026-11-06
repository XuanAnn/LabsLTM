package common;

import client.*;
import server.*;
import common.*;

import java.util.EnumSet;

/**
 * Các dòng SAVE bên dưới bật từng loại ghi dữ liệu. Muốn ngừng ghi loại nào: comment đúng một dòng
 * ENABLED.add(...), rồi build và chạy lại. Không comment lệnh JDBC executeUpdate/commit dùng chung.
 */
public final class SaveOptions {
	public enum Kind {
		AUDIT,
		ACCOUNT,
		SESSION,
		MAIL_INBOX,
		MAIL_SENT,
		TEXT_EXPORT
	}

	private static final EnumSet<Kind> ENABLED = EnumSet.noneOf(Kind.class);

	static {
		// Nhật ký sự kiện.
		ENABLED.add(Kind.AUDIT); // SAVE-AUDIT: comment dòng này để tắt.
		// Đăng ký tài khoản mới và tài khoản mẫu.
		ENABLED.add(Kind.ACCOUNT); // SAVE-ACCOUNT: comment dòng này để tắt.
		// Lưu phiên vào MySQL.
		ENABLED.add(Kind.SESSION); // SAVE-SESSION: comment dòng này để tắt.
		// Nhận thư local vào hộp thư; tắt mục này sẽ từ chối thư mới.
		ENABLED.add(Kind.MAIL_INBOX); // SAVE-MAIL_INBOX: comment dòng này để tắt.
		// Bản sao thư đã gửi, gồm thư gửi Gmail.
		ENABLED.add(Kind.MAIL_SENT); // SAVE-MAIL_SENT: comment dòng này để tắt.
		// Xuất bản sao TXT từ MySQL.
		ENABLED.add(Kind.TEXT_EXPORT); // SAVE-TEXT_EXPORT: comment dòng này để tắt.
	}

	private SaveOptions() {}

	public static boolean enabled(Kind kind) {
		return ENABLED.contains(kind);
	}
}
