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
										"Quản lý tệp",
										"FTP TCP control / passive data",
										"ftp",
										2121,
										0,
										new ServerController(),
										new AdminFrame.DataTab(
												"Files",
												new String[] {
													"Đường dẫn",
													"Người gửi",
													"Kích thước",
													"Cập nhật"
												},
												"SELECT path,owner,size,updated_at FROM files ORDER"
														+ " BY updated_at DESC"))
								.setVisible(true));
	}
}
