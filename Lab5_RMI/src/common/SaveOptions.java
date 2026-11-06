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
		RMI_DATA
	}

	private static final EnumSet<Kind> ENABLED = EnumSet.noneOf(Kind.class);

	static {
		// Nhật ký sự kiện.
		ENABLED.add(Kind.AUDIT); // SAVE-AUDIT: comment dòng này để tắt.
		// Đăng ký tài khoản mới và tài khoản mẫu.
		ENABLED.add(Kind.ACCOUNT); // SAVE-ACCOUNT: comment dòng này để tắt.
		// Lưu phiên vào MySQL.
		ENABLED.add(Kind.SESSION); // SAVE-SESSION: comment dòng này để tắt.
		// Ghi và đồng bộ giá trị RMI; tắt mục này sẽ từ chối thay đổi.
		ENABLED.add(Kind.RMI_DATA); // SAVE-RMI_DATA: comment dòng này để tắt.
	}

	private SaveOptions() {}

	public static boolean enabled(Kind kind) {
		return ENABLED.contains(kind);
	}
}
