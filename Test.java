import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class Test {

	private static final File tempDir = new File("temp");
	private static final String serverHost = "localhost";
	private static final int serverPort = 16789;

	private static final class UploadHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange x) throws IOException {
			String contentType = x.getRequestHeaders().getFirst("Content-Type");
			InputStream in = new BufferedInputStream(x.getRequestBody());
			try {
				byte[] response = "ok".getBytes();
				MultipartParser.parse(tempDir, in, contentType);
				x.getResponseHeaders().set("Content-Type", "text/plain");
				x.sendResponseHeaders(200, response.length);
				x.getResponseBody().write(response);
			} finally {
				x.close();
			}
		}
	}


	private static void testCopyToBoundary(

			String input,
			String boundary,
			String output,
			String stream_state) throws IOException {

		byte[] boundary_bytes = boundary.getBytes();

		PushbackInputStream in = new PushbackInputStream(
				new ByteArrayInputStream(input.getBytes()),
				boundary_bytes.length);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		MultipartParser.copyToBoundary(in, out, boundary.getBytes());

		String result = new String(out.toByteArray());

		if (!result.equals(output)) {
			System.err.println("FAIL: " + result + " != " + output);
			System.exit(1);
		}

		String left = toString(in);

		if (!stream_state.equals(left)) {
			System.err.println("Buggy stream state: " +
					stream_state + " != " + left);
			System.exit(1);
		}

		System.out.println("OK: " + input + " " + boundary + " " + output + " " + stream_state);

	}

	private static void testCopyToBoundary() throws IOException {
		testCopyToBoundary("abcd", "b", "a", "bcd");
		testCopyToBoundary("aaa", "aa", "", "aaa");
		testCopyToBoundary("abcde", "bc", "a", "bcde");
		try {
			testCopyToBoundary("aaaa", "bb", "aaaa", "");
			System.out.println("FAIL: Expected EOFException");
			System.exit(1);
		} catch (EOFException ignore) {
		}
	}

	private static void testUpload() throws IOException {
		tempDir.mkdirs();

		InetSocketAddress address = new InetSocketAddress(
				serverHost, serverPort);

		System.out.println("staring test server: " + serverHost + ":" + serverPort);
		HttpServer server = HttpServer.create(address, 20);
		server.createContext("/", new UploadHandler());
		server.start();

		byte[][] data = {
				createRandomBytes(),
				createRandomBytes(),
				createRandomBytes(),
				createRandomBytes()
		};


		MultipartEntity entity = new MultipartEntity();
		for (byte[] b : data)
			entity.addPart("files", new InputStreamBody(new ByteArrayInputStream(b), "test.x"));

		URI uri = URI.create("http://" + serverHost + ":" + serverPort + "/");
		HttpPost post = new HttpPost(uri);

		post.setEntity(entity);

		HttpClient client = new DefaultHttpClient();

		client.execute(post);

		server.stop(0);

		Set<String> checkSums = new HashSet<String>();
		File[] files = tempDir.listFiles();
		if (files != null)
			for (File file : files)
				checkSums.add(md5sum(file));

		for (byte[] input : data) {
			String sum = md5sum(input);
			if (checkSums.contains(sum)) {
				System.out.println("OK: md5 sum found: " + sum);
			} else {
				System.out.println("Fail: Checksum not found: " + sum);
				System.exit(1);
			}
		}


		/*
		 * Cleanup
		 */
		files = tempDir.listFiles();
		if (files != null)
			for (File file : files)
				file.delete();
		tempDir.delete();

	}

	public static void main(String[] args) throws Exception {
		testCopyToBoundary();
		testUpload();

	}


	private static byte[] createRandomBytes() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Random random = new Random();
		int n = random.nextInt(5000) + 100;
		for (int i = 0; i != n; i++) {
			out.write(random.nextInt(256));
		}
		return out.toByteArray();

	}

	/**
	 *
	 */
	private static String toString(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[0x1000];
		for (int n = in.read(buf); n != -1; n = in.read(buf))
			out.write(buf, 0, n);
		return new String(out.toByteArray());
	}

	/**
	 * Reads {@ink InputStream} and calculates its md5 sum.
	 * The sum is returned as an lowercase hex string.
	 *
	 * @param in th stream to read.
	 * @return the lowercase md5 hex string.
	 */
	private static String md5sum(InputStream in) throws IOException {

		/*
		 * Create the message digest.
		 */
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		/*
		 * Update the message digest.
		 */
		byte[] buf = new byte[0x2000];
		for (int n = in.read(buf); n != -1; n = in.read(buf))
			md.update(buf, 0, n);

		byte[] digest = md.digest();

		/*
		 * Build the lowercase hex string.
		 */
		StringBuilder sb = new StringBuilder();
		for (byte b : digest) {
			int i = b & 0xFF;
			sb.append(String.format("%02x", i));
		}
		return sb.toString();


	}

	/**
	 * Calculates the md5 sum of input.
	 *
	 * @param input the byte array to calculate the md5 sum for.
	 */
	private static String md5sum(byte[] input) throws IOException {
		return md5sum(new ByteArrayInputStream(input));
	}

	/**
	 * Calculates the md5 sum of file.  The sum is returned
	 * as an lowercase hex string.
	 *
	 * @param file the file in question.
	 * @return the lowercase md5 hex string.
	 */
	private static String md5sum(File file) throws IOException {

		InputStream in = null;

		try {
			in = new FileInputStream(file);
			return md5sum(in);
		} finally {
			if (in != null)
				in.close();
		}
	}


}
