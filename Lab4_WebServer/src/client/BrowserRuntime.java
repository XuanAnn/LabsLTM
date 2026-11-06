package client;

import client.*;
import server.*;
import common.*;

import me.friwi.jcefmaven.*;

import org.cef.CefApp;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** This lab's native browser installation stays outside target so rebuilds retain it. */
public final class BrowserRuntime {
	private static CompletableFuture<CefApp> instance;

	private static Path cacheDirectory() {
		return LabPaths.resolve(Path.of(System.getProperty("lab.browser.cache", "browser-cache")));
	}

	private static CefAppBuilder builder(Consumer<String> progress) {
		CefAppBuilder cefAppBuilder = new CefAppBuilder();
		cefAppBuilder.setInstallDir(LabPaths.resolve(Path.of("browser-runtime")).toFile());
		cefAppBuilder.getCefSettings().windowless_rendering_enabled = false;
		cefAppBuilder.addJcefArgs("--disable-gpu");
		cefAppBuilder.getCefSettings().locale = "vi";
		Path cache =
				cacheDirectory();
		cefAppBuilder.getCefSettings().log_file = cache.resolve("browser.log").toString();
		cefAppBuilder.getCefSettings().cache_path = cache.toString();
		cefAppBuilder.getCefSettings().log_severity =
				org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR;
		cefAppBuilder.setProgressHandler(
				(phase, percent) ->
						progress.accept(
								switch (phase) {
									case DOWNLOADING ->
											"Đang tải bộ hiển thị lần đầu"
													+ (percent >= 0
															? " · " + (int) percent + "%"
															: "…");
									case EXTRACTING, INSTALL -> "Đang chuẩn bị bộ hiển thị…";
									default -> "Đang mở trình duyệt…";
								}));
		return cefAppBuilder;
	}

	static synchronized CompletableFuture<CefApp> get(Consumer<String> progress) {
		if (instance == null) {
			instance = new CompletableFuture<>();
			Thread.startVirtualThread(
					() -> {
						try {
							Files.createDirectories(cacheDirectory());
							CefApp app = builder(progress).build();
							instance.complete(app);
						} catch (Throwable exception) {
							instance.completeExceptionally(exception);
						}
					});
		}
		return instance;
	}

	public static synchronized void shutdown() {
		if (instance != null) {
			instance.thenAccept(CefApp::dispose);
		}
	}

	public static void main(String[] args) throws Exception {
		builder(System.out::println).install();
		System.out.println("Browser runtime ready: " + LabPaths.resolve(Path.of("browser-runtime")));
	}
}
