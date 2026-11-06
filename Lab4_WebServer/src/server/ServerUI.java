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
										"Quản lý truy cập",
										"HTTP/1.1",
										"web",
										8080,
										0,
										new ServerController(),
										new AdminFrame.DataTab(
												"Lượt truy cập",
												new String[] {
													"Thời gian",
													"Thao tác",
													"Method",
													"URL",
													"IP Client",
													"Status",
													"Bytes",
													"Phiên"
												},
												"SELECT"
													+ " at,action,method,target,remote_ip,status,bytes,session_id"
													+ " FROM requests ORDER BY id DESC LIMIT 300"))
								.setVisible(true));
	}
}
