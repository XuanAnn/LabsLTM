package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tìm thư mục trang web của Lab 4, kể cả khi IDE chạy từ workspace. */
public final class WebPaths {
	private WebPaths() {}

	public static Path documentRoot() throws IOException {
		String configured = System.getProperty("lab.web.root");
		if (configured != null && !configured.isBlank()) {
			return Path.of(configured).toAbsolutePath().normalize();
		}
		try {
			Path classes = Path.of(WebPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return findDocumentRoot(Path.of(""), classes);
		} catch (java.net.URISyntaxException exception) {
			throw new IOException("Không xác định được thư mục trang web", exception);
		}
	}

	public static Path findDocumentRoot(Path workingDirectory, Path classes) throws IOException {
		for (Path folder = classes.toAbsolutePath().normalize(); folder != null; folder = folder.getParent()) {
			Path candidate = folder.resolve("public");
			if (Files.isRegularFile(candidate.resolve("index.html"))) {
				return candidate;
			}
		}
		Path working = workingDirectory.toAbsolutePath().normalize();
		Path labPublic = working.resolve("Lab4_WebServer/public");
		if (Files.isRegularFile(labPublic.resolve("index.html"))) {
			return labPublic;
		}
		Path localPublic = working.resolve("public");
		if (Files.isRegularFile(localPublic.resolve("index.html"))) {
			return localPublic;
		}
		throw new IOException("Không tìm thấy trang chủ. Cần có thư mục public chứa index.html của Lab 4.");
	}
}
