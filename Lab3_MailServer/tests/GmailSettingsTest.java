import client.GmailSettings;
import java.nio.file.*;

public class GmailSettingsTest {
	private static int checks;

	private static void check(boolean condition) {
		if (!condition) throw new AssertionError("Gmail settings check " + (checks + 1));
		checks++;
	}

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("gmail-settings-test-");
		Path lab = root.resolve("Lab3_MailServer");
		Files.createDirectories(lab.resolve("src/client"));
		Files.writeString(lab.resolve("src/client/GmailSettings.java"), "// test marker");
		Path expected = lab.resolve("config/gmail.local.properties");
		check(GmailSettings.resolveFile(root, lab.resolve("target/classes")).equals(expected));
		check(GmailSettings.resolveFile(root, lab.resolve("target/lab-mail-1.0.jar")).equals(expected));
		check(GmailSettings.resolveFile(root, root.resolve("out/classes")).equals(expected));
		check(GmailSettings.resolveFile(lab, root.resolve("other/classes")).equals(expected));
		String original = System.getProperty("lab.gmail.config");
		try {
			System.setProperty("lab.gmail.config", expected.toString());
			check(!GmailSettings.load().configured());
			new GmailSettings("demo@example.test", "ab cd ef").save();
			check(GmailSettings.load().configured());
			check(GmailSettings.load().account().equals("demo@example.test"));
			check(GmailSettings.load().password().equals("abcdef"));
		} finally {
			if (original == null) System.clearProperty("lab.gmail.config");
			else System.setProperty("lab.gmail.config", original);
		}
		System.out.println("ALL " + checks + " GMAIL SETTINGS CHECKS PASSED; no mail sent");
	}
}
