/*
 * Multipart.java - one file http multipart parser -
 *
 * Copyright (c) 2021, Olaf Ritter von Ruppert
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
	 * The default file type.
	 */
	private static final String DEFAULT_FILE_TYPE = "unknown";

	/**
	 * Parses a multipart request.
	 * @return a list of multipart items.
	 * @param tempDir the directory to create item files in.
	 * @param in the input stream to read from.
	 * @param contentType the content type http header.
	 */
	public static List<Item> parse(
		File tempDir,
		InputStream in,
		String contentType) throws IOException {

		if (contentType == null)
			throw new NoContentType();

		if (!contentType.startsWith("multipart/form-data"))
			throw new WrongContentType();

		/*
		 * Extract boundary from contentType.
		 */
		String boundaryString = null;

		for (String part : contentType.split(";"))
			if (part.trim().startsWith("boundary="))
				boundaryString =  part.trim().substring(9);

		if (boundaryString == null)
			throw new NoBoundary();

		boundaryString = "\r\n--" + boundaryString;

		byte[] boundary = boundaryString.getBytes(US_ASCII);

		List<Item> result = new ArrayList<Item>();
		try {
			parse(tempDir,
			      new PushbackInputStream(in, boundary.length),
			      boundary,
			      result);
			return result;
		} catch (Exception e) {
			/*
			 * Cleanup on error.
			 */
			for (Item item : result)
				item.file.delete();
			throw new RuntimeException(e);
		}

	}

	/**
	 * Parses a multipart request.
	 * @param tempDir the directory where item files are created.
	 * @param in the {@link PushbackInputStream} to read from.
	 * @param boundary the boundary array.
	 * @param result the list to append items to.
	 */
	private static void parse(
		File tempDir,
		PushbackInputStream in,
		byte[] boundary,
		List<Item> result) throws IOException {


	start:
		for (;;) {

			String fieldName = null;
			String uploadName = null;

			/*
			 * Parse headers
			 */
			while (true) {
				String line = readLine(in);

				if (line.equals("--"))
					break start;

				if (line.isEmpty())
					break;

				if (!line.startsWith("Content-Disposition: "))
					continue;

				for (String part : line.split(";")) {

					part = part.trim();

					if (part.startsWith("name="))
						fieldName = getFieldValue(part);

					if (part.startsWith("filename="))
						uploadName = getFieldValue(part);

				}
			}

			Item item = createItem(tempDir, fieldName, uploadName);

			OutputStream out = null;

			try {
				out = new FileOutputStream(item.file);
				out = new BufferedOutputStream(out);
				copyToBoundary(in, out, boundary);
				result.add(item);
				in.skip(boundary.length);
			} finally {
				if (out != null)
					out.close();
			}

		}

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
	 * type is everything after the last dot.  If the file type
	 * could not be determined, return {@link DEFAULT_FILE_TYPE}.
	 * @return the file type or {@link DEFAULT_FILE_TYPE}.
	 * @param name the filename in question.
	 */
	static String getFileType(String filename) {

		if (filename == null)
			return DEFAULT_FILE_TYPE;

		int index = filename.lastIndexOf('.');

		if (index == -1)
			return DEFAULT_FILE_TYPE;

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
	 * Given a field, extract its value.
	 * E.g. <code>"name=\"value\"" -&gt; "value"</code>
	 * @param field the field in question.
	 * @return the field value.
	 */
	private static String getFieldValue(String field) {

		String[] parts = field.split("=", 2);

		if (parts.length != 2)
			return null;

		String value = parts[1].trim();

		if (!value.startsWith("\""))
			return value;

		if (value.startsWith("\""))
			value = value.substring(1);

		if (value.endsWith("\""))
			value = value.substring(0, value.length() -1);

		try {
			return URLDecoder.decode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}


	}




}
