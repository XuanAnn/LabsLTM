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
		FILE_METADATA
	}

	private static final EnumSet<Kind> ENABLED = EnumSet.noneOf(Kind.class);

	static {
		// Nhật ký sự kiện.
		ENABLED.add(Kind.AUDIT); // SAVE-AUDIT: comment dòng này để tắt.
		// Đăng ký tài khoản mới và tài khoản mẫu.
		ENABLED.add(Kind.ACCOUNT); // SAVE-ACCOUNT: comment dòng này để tắt.
		// Lưu phiên vào MySQL.
		ENABLED.add(Kind.SESSION); // SAVE-SESSION: comment dòng này để tắt.
		// Thông tin file FTP (file thật vẫn lưu trên đĩa).
		ENABLED.add(Kind.FILE_METADATA); // SAVE-FILE_METADATA: comment dòng này để tắt.
	}

	private SaveOptions() {}

	public static boolean enabled(Kind kind) {
		return ENABLED.contains(kind);
	}
}
