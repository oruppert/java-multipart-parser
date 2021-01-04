import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

public final class TestServer implements HttpHandler {

	@Override
	public void handle(HttpExchange x) throws IOException {

		try {

			String method = x.getRequestMethod();

			if (method.equals("GET")) {
				sendHtml(x, 200, readFile("test.html"));
				return;
			}

			if (method.equals("POST")) {
				handlePost(x);
				x.getResponseHeaders().set(
					"Location", "/");
				sendHtml(x, 302, "<h1>Redirect...");
				return;
			}

			sendHtml(x, 404, "<h1>404 Not Found");

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);

		} finally {
			x.close();
		}
	}

	private static void handlePost(HttpExchange x) throws IOException {

		File tempDir = new File("temp");

		tempDir.mkdirs();

		MultipartParser.parse(
			tempDir,
			x.getRequestBody(),
			x.getRequestHeaders().getFirst("Content-Type"));


	}

	public static void main(String[] args) throws IOException {

		InetSocketAddress address =
			new InetSocketAddress("localhost", 8000);

		HttpServer server = HttpServer.create(address, 30);
		server.createContext("/", new TestServer());
		server.start();

	}

	private static void sendHtml(HttpExchange x, int status, String html)
		throws IOException {

		byte[] bytes = html.getBytes("UTF-8");

		x.getResponseHeaders().set(
			"Content-Type", "text/html; charset=utf-8");

		x.sendResponseHeaders(status, bytes.length);
		x.getResponseBody().write(bytes);


	}

	private static String readFile(String filename) throws IOException {

		InputStream in = null;

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {

			in = new FileInputStream(filename);

			byte[] buf = new byte[0x1000];
			for (int n = in.read(buf); n != -1; n = in.read(buf))
				out.write(buf, 0, n);

			return new String(out.toByteArray(), "UTF-8");

		} finally {
			if (in != null)
				in.close();
		}

	}

}
