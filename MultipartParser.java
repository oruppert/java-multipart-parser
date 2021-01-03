import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses multipart http requests.
 */
public final class MultipartParser {

	/**
	 * The base class for all multipart exceptions.
	 */
	public static class MultipartException extends RuntimeException {
	}

	/**
	 * Thrown when the http content type header is missing.
	 */
	public static final class NoContentType extends MultipartException {
	}

	/**
	 * Thrown when the request has the wrong content type.
	 */
	public static final class WrongContentType extends MultipartException {
	}

	/**
	 * Thrown when the boundary field is missing.
	 */
	public static final class NoBoundary extends MultipartException {
	}

	/**
	 * The ascii charset.
	 */
	private static final Charset US_ASCII = Charset.forName("US-ASCII");

	/**
	 * The utf-8 charset.
	 */
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	/**
	 * Parses a multipart request.
	 * @return a list of multipart items.
	 */
	public static List<Item> parse(InputStream in, String contentType)
		throws IOException{

		List<Item> result = new ArrayList<Item>();

		if (contentType == null)
			throw new NoContentType();

		if (!contentType.startsWith("multipart/form-data"))
			throw new WrongContentType();

		String boundaryString = getBoundary(contentType);

		if (boundaryString == null)
			throw new NoBoundary();

		boundaryString = "--" + boundaryString + "--";

		byte[] boundary = boundaryString.getBytes(US_ASCII);

		PushbackInputStream pi = new PushbackInputStream(in, boundary.length);

		while (true) {
			String line = readLine(pi);
			System.out.println(line);
			if (line.isEmpty())
				break;

		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		copyToBoundary(pi, out, boundary);
		System.out.println(new String(out.toByteArray()));


		return result;

	}


	/**
	 * A multipart item.
	 */
	public static final class Item {

		/**
		 * The local file where the item contents are stored.
		 */
		public final File file;

		/**
		 * The upload input field name.
		 */
		public final String fieldName;

		/**
		 * The orginal filename from the client machine.
		 */
		public final String uploadName;

		/**
		 * Constructs an item.
		 */
		public Item(File file, String fieldName, String uploadName) {
			this.file = file;
			this.fieldName = fieldName;
			this.uploadName = uploadName;
		}
	}

	/**
	 * Creates a new item. The item file is created in
	 * <code>dir</code>. Tries to preserve the file type of
	 * <code>uploadName</code>.
	 * @param dir the directory where the item file is created.
	 * @param fieldName the upload input field name.
	 * @param uploadName the original filename from the client machine.
	 */
	static Item createItem(File dir, String fieldName, String uploadName)
			throws IOException {
		String type = getFileType(uploadName);
		File file = File.createTempFile("upload-", "." + type, dir);
		return new Item(file, fieldName, uploadName);
	}

	/**
	 * Returns the file type as a lower case string.  The file
	 * type is everything after the last dot.
	 * @param name the filename in question.
	 */
	static String getFileType(String filename) {
		int index = filename.lastIndexOf('.');
		return filename.substring(index + 1).toLowerCase();
	}

	/**
	 * Copies <code>in</code> to <code>out<code> until boundary is
	 * reached.  The {@link PushbackInputStream} size must be
	 * greater or equal than <code>boundary.length</code>.
	 * @param in the {@link PushbackInputStream} to read from.
	 * @param out the {@link OutputStream} to write to.
	 * @param boundary the boundary array.
	 */
	static void copyToBoundary(
		PushbackInputStream in,
		OutputStream out,
		byte[] boundary) throws IOException {

	start:
		while (true) {

			for (int i = 0; i != boundary.length; i++) {

				int c = in.read();

				if (c == -1)
					throw new EOFException();

				if (c == (boundary[i] & 0xff))
					continue;

				in.unread(c);
				in.unread(boundary, 0, i);
				out.write(in.read());
				continue start;

			}

			in.unread(boundary);
			return;

		}


	}

	/**
	 * Reads a "\r\n" terminated line from <code>in</code>.
	 * Uses the ascii charset to convert bytes.
	 * The "\r\n" sequence is not part of the returned string.
	 * @param in the {@link InputStream} to read from.
	 * @return the read line.
	 */
	static String readLine(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int p = -1; /* previous byte */
		while (true) {

			int c = in.read();

			if (c == -1)
				throw new EOFException();

			if (p == '\r' && c == '\n')
				break;

			if (p != -1)
				out.write(p);

			p = c;

		}
		return new String(out.toByteArray(), US_ASCII);
	}

	/**
	 * Returns the content type boundary string or null if there
	 * is no such thing.
	 */
	static String getBoundary(String contentType) {
		for (String part : contentType.split(";"))
			if (part.trim().startsWith("boundary="))
				return part.trim().substring(9);
		return null;
	}

}
