package server;

import client.*;
import server.*;
import common.*;

import common.AdminFrame;

public final class ServerController implements AdminFrame.Service {
	private volatile boolean running;
	private volatile long generation;
	private AutoCloseable handle;

	public synchronized void start(AdminFrame.Settings settings) throws Exception {
		if (running) {
			return;
		}
		generation++;
		running = true;
		try {
			MailServer server = new MailServer(settings.config());
			handle = server;
			run(() -> server.serve(settings.port(), settings.secondPort()));
		} catch (Exception exception) {
			running = false;
			if (handle != null) {
				handle.close();
			}
			throw exception;
		}
	}

	private void run(Task task) {
		long token = generation;
		Thread.startVirtualThread(
				() -> {
					try {
						task.run();
					} catch (Exception exception) {
						if (running) {
							System.err.println(exception.getMessage());
						}
					} finally {
						if (token == generation) {
							running = false;
						}
					}
				});
	}

	public synchronized void stop() throws Exception {
		generation++;
		running = false;
		if (handle != null) {
			handle.close();
			handle = null;
		}
	}

	public boolean running() {
		return running;
	}

	private interface Task {
		void run() throws Exception;
	}
}
