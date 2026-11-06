package server;

import client.*;
import server.*;
import common.*;

import common.*;

import javax.swing.*;

public final class ServerUI {
	public static void main(String[] args) {
		DesktopUi.init();
		SwingUtilities.invokeLater(
				() ->
						new AdminFrame(
										"Quản lý thư",
											"SMTP / POP3",
										"mail",
										2525,
										2110,
										new ServerController(),
										new AdminFrame.DataTab(
												"Đã gửi",
												new String[] {
													"ID",
													"Tài khoản",
													"Người nhận",
													"Người gửi",
													"Ngày gửi"
												},
												"SELECT id,owner,recipient,sender,created_at FROM"
														+ " sent_mail ORDER BY id DESC LIMIT 300"),
										new AdminFrame.DataTab(
												"Thư đến",
												new String[] {
													"ID",
													"Người nhận",
													"Người gửi",
													"Byte",
													"Thời gian"
												},
												"SELECT id,recipient,sender,LENGTH(body),created_at"
														+ " FROM mail ORDER BY id DESC LIMIT 300"))
								.setVisible(true));
	}
}
