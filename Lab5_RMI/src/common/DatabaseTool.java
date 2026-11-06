package common;

import client.*;
import server.*;
import common.*;

import java.nio.file.Path;
import java.sql.Connection;

public final class DatabaseTool {
	public static void main(String[] args) throws Exception {
		Db db = args.length == 0 ? new Db() : new Db(Path.of(args[0]));
		try (Connection connection = db.open()) {
			System.out.println(
					"CONNECTED MySQL "
							+ connection.getMetaData().getDatabaseProductVersion()
							+ " / schema="
							+ db.name());
		}
		System.out.println("Schema initialized; tables and data can be viewed in HeidiSQL.");
	}
}
