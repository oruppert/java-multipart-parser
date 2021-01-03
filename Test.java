import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.IOException;

public final class Test {

	static void testCopyToBoundary(
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

	}


	public static void main(String[] args) throws Exception {
		testCopyToBoundary("abcd", "b", "a", "bcd");
		testCopyToBoundary("aaa", "aa", "", "aaa");
		testCopyToBoundary("abcde", "bc", "a", "bcde");
		//testCopyToBoundary("aaaa", "bb", "aaaa", "");

	}

	private static String toString(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[0x1000];
		for (int n = in.read(buf); n != -1; n = in.read(buf))
			out.write(buf, 0, n);
		return new String(out.toByteArray());
	}


}
