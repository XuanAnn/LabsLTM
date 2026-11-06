package client;

import client.*;
import server.*;
import common.*;

import java.util.*;

public final class ClientController {
	private Store store;

	public void connect(String host, int port) throws Exception {
		store = Client.connect(host, port);
		store.info();
	}

	public Map<String, Store.Value> snapshot() throws Exception {
		return store.snapshot();
	}

	public Store.Value put(String key, String value) throws Exception {
		return store.put(key, value, false);
	}

	public Store.Value delete(String key) throws Exception {
		return store.put(key, "", true);
	}

	public String info() throws Exception {
		return store.info();
	}
}
