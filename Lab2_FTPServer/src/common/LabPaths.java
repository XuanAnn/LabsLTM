package common;

import java.nio.file.*;

/** Mỗi lab tự tìm thư mục của mình, không ghi dữ liệu vào workspace chung. */
public final class LabPaths {
	private LabPaths() {}

	public static Path root() {
		try {
			Path location = Path.of(LabPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			for (Path folder = location.toAbsolutePath(); folder != null; folder = folder.getParent()) {
				if (Files.isRegularFile(folder.resolve("pom.xml")) || folder.getFileName() != null
						&& folder.getFileName().toString().equals("Lab2_FTPServer")) {
					return folder;
				}
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Không xác định được thư mục lab", exception);
		}
		Path working = Path.of("").toAbsolutePath();
		Path nested = working.resolve("Lab2_FTPServer");
		return Files.isDirectory(nested) ? nested : working;
	}

	public static Path resolve(Path path) {
		return path.isAbsolute() ? path.normalize() : root().resolve(path).normalize();
	}
}
