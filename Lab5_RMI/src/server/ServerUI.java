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
				() -> {
						AdminFrame frame = new AdminFrame(
										"Quản lý đồng bộ dữ liệu",
										"Java RMI / đồng bộ hai MySQL schemas",
										"rmi",
										1099,
										1199,
										new ServerController(),
										new AdminFrame.DataTab(
												"Dữ liệu đồng bộ",
												new String[] {
													"Key", "Value", "Version", "Node", "Đã xóa"
												},
												"SELECT"
													+ " entry_key,entry_value,version,origin,deleted"
													+ " FROM entries ORDER BY entry_key"));
						if (args.length >= 3) {
							frame.configureNode(args[0], args[1], args[2]);
						}
						frame.setVisible(true);
					});
	}
}
