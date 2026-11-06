import client.BrowserAddress;

public class BrowserAddressTest {
	private static int checks;

	private static void expect(String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
		checks++;
	}

	public static void main(String[] args) {
		String actual = "http://127.0.0.1:8080/videos";
		expect("http://127.0.0.1/videos", BrowserAddress.display(actual));
		expect(actual, BrowserAddress.resolve(BrowserAddress.display(actual), actual));
		expect("http://127.0.0.1:8080/music", BrowserAddress.resolve("127.0.0.1/music", actual));
		expect("http://127.0.0.1:8080/photos", BrowserAddress.resolve("/photos", actual));
		expect("http://127.0.0.1:9090/music", BrowserAddress.resolve("http://127.0.0.1:9090/music", actual));
		expect("https://example.org/page", BrowserAddress.resolve("https://example.org/page", actual));
		expect("http://[::1]/a", BrowserAddress.display("http://[::1]:8080/a"));
		String encoded = "http://127.0.0.1:8080/a%2Fb?q=x%20y#part";
		expect(encoded, BrowserAddress.resolve(BrowserAddress.display(encoded), actual));
		System.out.println("ALL " + checks + " ADDRESS CHECKS PASSED");
	}
}
