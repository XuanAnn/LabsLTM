package common;

import client.*;
import server.*;
import common.*;

import java.io.Serializable;
import java.rmi.*;
import java.util.Map;

public interface Store extends Remote {
	record Value(String text, long version, String origin, boolean deleted)
			implements Serializable {
		public int compare(Value other) {
			int count = Long.compare(version, other.version);
			return count != 0 ? count : origin.compareTo(other.origin);
		}
	}

	Map<String, Value> snapshot() throws RemoteException;

	Value put(String key, String text, boolean delete) throws RemoteException;

	void merge(Map<String, Value> values) throws RemoteException;

	String info() throws RemoteException;
}
