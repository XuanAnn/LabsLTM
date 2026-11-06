package client;

import client.*;
import server.*;
import common.*;

import common.*;

import java.rmi.registry.*;

import javax.swing.*;

public final class Client {
	public static Store connect(String host, int port) throws Exception {
		return (Store) LocateRegistry.getRegistry(host, port).lookup("Store");
	}

	public static void main(String[] arguments) {
		System.setProperty("sun.rmi.transport.tcp.responseTimeout", "5000");
		SwingUtilities.invokeLater(
				() -> {
					Desk ui = new Desk("Lab 5 • Java RMI replicated data");
					ui.field("Server host", Wire.arg(arguments, 0, "localhost"));
					ui.field("Registry port", Wire.arg(arguments, 1, "1099"));
					ui.field("Key", "student:001");
					ui.field("Value", "Nguyễn Văn A");
					ui.button(
							"Read all",
							() -> {
								Store store =
										connect(
												ui.get("Server host"),
												Integer.parseInt(ui.get("Registry port")));
								StringBuilder textBuilder = new StringBuilder(store.info() + "\n");
								java.util.Map<String, Store.Value> entries =
										new java.util.TreeMap<>(store.snapshot());
								for (java.util.Map.Entry<String, Store.Value> entry :
										entries.entrySet()) {
									textBuilder.append(entry.getKey());
									textBuilder.append(" = ");
									textBuilder.append(entry.getValue());
									textBuilder.append('\n');
								}
								return textBuilder.toString();
							});
					ui.button(
							"Put",
							() ->
									connect(
													ui.get("Server host"),
													Integer.parseInt(ui.get("Registry port")))
											.put(ui.get("Key"), ui.get("Value"), false)
											.toString());
					ui.button(
							"Delete",
							() ->
									connect(
													ui.get("Server host"),
													Integer.parseInt(ui.get("Registry port")))
											.put(ui.get("Key"), "", true)
											.toString());
					ui.show();
				});
	}
}
