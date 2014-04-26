/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package net.cloud.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.kvstore.KVStoreFactory;
import org.kvstore.holders.DataHolder;
import org.kvstore.io.FileStreamStore;
import org.kvstore.io.StringSerializer;
import org.kvstore.structures.btree.BplusTree.InvalidDataException;
import org.kvstore.structures.btree.BplusTreeFile;
import org.packer.Base64;

/**
 * Simple URL Shortener
 * 
 * @author Guillermo Grandes / guillermo.grandes[at]gmail.com
 */
public class TinyURL extends HttpServlet {
	private static final Logger log = Logger.getLogger(TinyURL.class);
	private static final long serialVersionUID = 1L;
	//
	private static final int MIN_URL_LENGTH = 12;
	private static final int KEY_SPACE = 6;
	private static final int MAX_COLLISION = 5;
	private static final int CONNECTION_TIMEOUT = 3000; // millis
	private static final int READ_TIMEOUT = 3000; // millis
	/**
	 * CHECK_LEVEL
	 * 0=none, 1=URLSyntax, 2=SURBL, 3=URLConnection, 4=WhiteList
	 */
	private static int CHECK_LEVEL = 4;
	//
	private static final Charset iso = Charset.forName("ISO-8859-1");
	private PersistentStorage store;
	private MessageDigest md;
	private SURBL surbl;

	public TinyURL() {
	}

	@Override
	public void init() throws ServletException {
		final String storeDir = getServletContext().getRealPath("/WEB-INF/storage/");
		log.info("ServletContext StoragePath: " + storeDir);
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new ServletException("Unable to instantiate MessageDigest", e);
		}
		try {
			store = new PersistentStorage(storeDir);
			store.open();
		} catch (Exception e) {
			throw new ServletException("Unable to instantiate PersistentStorage", e);
		}
		try {
			surbl = new SURBL(storeDir);
			surbl.load();
		} catch (Exception e) {
			throw new ServletException("Unable to instantiate SURBL", e);
		}
	}

	@Override
	public void destroy() {
		store.close();
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		final PrintWriter out = response.getWriter();
		final String key = getPathInfoKey(request.getPathInfo());
		if (key != null) {
			final MetaHolder meta = store.get(key);
			if (meta != null) {
				// Found - send response
				response.sendRedirect(meta.url);
				return;
			}
		}
		sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Not Found");
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		final PrintWriter out = response.getWriter();
		final String url = request.getParameter("url");
		if ((url == null) || (url.length() < MIN_URL_LENGTH)) {
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL Parameter");
			return;
		}
		String key = hashURL(url);
		int collision = 0;
		while (true) { // Handle possible collisions
			final MetaHolder meta = store.get(key);
			// Dont exists
			if (meta == null)
				break;
			// Duplicated
			if (url.equals(meta.url)) {
				sendResponse(response, out, url, key, collision);
				return;
			}
			// Collision
			if (++collision > MAX_COLLISION) {
				log.error("Too many collisions { url=" + url + " id=" + key + " }");
				sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"ERROR: Unable to Short URL");
				return;
			}
			key = hashURL(Integer.toString(collision) + ":" + url);
		}
		// Check URL validity
		try {
			checkURL(url);
		} catch (Exception e) {
			log.error("Invalid URL: " + e.toString());
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL Parameter ("
					+ e.getClass().getSimpleName() + ")");
			return;
		}
		// Store new URL
		store.put(key, url);
		sendResponse(response, out, url, key, collision);
	}

	private final void checkURL(final String url) throws Exception {
		if (CHECK_LEVEL < 1)
			return;
		InputStream is = null;
		URLConnection conn = null;
		try {
			final URL urlCheck = new URL(url);
			if (CHECK_LEVEL < 2)
				return;
			if (surbl.checkSURBL(urlCheck.getHost())) {
				throw new SpamDomainException("Spam domain detected");
			}
			if (CHECK_LEVEL < 3)
				return;
			conn = urlCheck.openConnection();
			conn.setConnectTimeout(CONNECTION_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			conn.setDoOutput(false);
			conn.setUseCaches(true);
			if (conn instanceof HttpURLConnection) {
				((HttpURLConnection) conn).setRequestMethod("HEAD");
			}
			conn.connect();
			is = conn.getInputStream();
			byte[] buf = new byte[2048];
			while (is.read(buf) > 0)
				;
			if (CHECK_LEVEL < 4)
				return;
			// TODO: Do reg-exp/whitelist check
		} finally {
			try {
				is.close();
			} catch (Throwable ign) {
			}
			try {
				((HttpURLConnection) conn).disconnect();
			} catch (Throwable ign) {
			}
		}
	}

	private static final void sendResponse(final HttpServletResponse response, final PrintWriter out,
			final String url, final String key, final int collision) {
		final String res = "{ \"id\": \"" + key + "\" }";
		// Send Response
		response.setContentType("application/json");
		out.println(res);
		log.log((collision > 0 ? Level.WARN : Level.INFO), "Request: url=" + url + " id=" + key
				+ " Response: " + res + " collition=" + collision);
	}

	private static final void sendError(final HttpServletResponse response, final PrintWriter out,
			final int status, final String msg) {
		response.setContentType("text/plain; charset=ISO-8859-1");
		response.setStatus(status);
		out.println(msg);
	}

	private static final String getPathInfoKey(final String pathInfo) {
		if (pathInfo == null)
			return null;
		if (pathInfo.isEmpty())
			return null;
		return pathInfo.substring(1);
	}

	private final String hashURL(final String url) {
		byte[] b = url.getBytes(iso);
		synchronized (md) {
			b = md.digest(b);
			md.reset();
		}
		return new String(Base64.encode(b, true), iso).substring(0, KEY_SPACE);
	}

	static class SURBL {
		// References:
		// http://www.surbl.org/redirection-sites
		// http://spamlinks.net/filter-dnsbl-lists.htm
		// http://www.surbl.org/guidelines
		// http://multirbl.valli.org/list/
		// http://www.rfc-editor.org/rfc/rfc5782.txt
		final String TWO_LEVEL_TLDS = "http://www.surbl.org/tld/two-level-tlds";
		final String THREE_LEVEL_TLDS = "http://www.surbl.org/tld/three-level-tlds";
		final File storeDir;
		final File storeLevel2;
		final File storeLevel3;
		final Set<String> set2 = Collections.synchronizedSet(new HashSet<String>(8192));
		final Set<String> set3 = Collections.synchronizedSet(new HashSet<String>(512));

		public SURBL(final String storeDirName) throws IOException {
			storeDir = new File(storeDirName);
			if (!storeDir.exists()) {
				if (!storeDir.mkdirs())
					throw new IOException("Invalid storeDir: " + storeDirName);
			}
			storeLevel2 = new File(storeDir, "tlds.2");
			storeLevel3 = new File(storeDir, "tlds.3");
		}

		public void load() throws IOException {
			final long now = System.currentTimeMillis();
			final long expire = (24 * 3600 * 1000L);
			// Get two-level-tlds
			if ((storeLevel2.lastModified() + expire) < now) {
				getTLDS(TWO_LEVEL_TLDS, storeLevel2);
			}
			// Get three-level-tlds
			if ((storeLevel3.lastModified() + expire) < now) {
				getTLDS(THREE_LEVEL_TLDS, storeLevel3);
			}
			// Load in memory
			loadSetFromFile(storeLevel2, set2);
			loadSetFromFile(storeLevel3, set3);
		}

		private static void loadSetFromFile(final File f, final Set<String> s) throws IOException {
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(f));
				String line = null;
				s.clear();
				while ((line = reader.readLine()) != null) {
					s.add(line);
				}
				log.info("Loaded " + s.size() + " TLDs from " + f.getName());
			} finally {
				reader.close();
			}
		}

		private static void getTLDS(final String inputUrl, final File cacheFile) throws IOException {
			final URL url = new URL(inputUrl);
			final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(CONNECTION_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			conn.setDoOutput(false);
			conn.setUseCaches(true);
			conn.setIfModifiedSince(cacheFile.lastModified());
			conn.connect();
			InputStream is = null;
			OutputStream os = null;
			try {
				is = conn.getInputStream();
				if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					os = new FileOutputStream(cacheFile, false);
					byte[] buf = new byte[4096];
					int len = 0;
					while ((len = is.read(buf)) > 0) {
						os.write(buf, 0, len);
					}
					log.info("HTTP_OK: " + inputUrl);
				} else if (conn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
					log.info("HTTP_NOT_MODIFIED: " + inputUrl);
				}
			} finally {
				try {
					is.close();
				} catch (Throwable ign) {
				}
				try {
					os.close();
				} catch (Throwable ign) {
				}
				try {
					conn.disconnect();
				} catch (Throwable ign) {
				}
			}
		}

		public boolean checkSURBL(final String host) throws UnknownHostException, MalformedURLException {
			final InetAddress inetAddr = InetAddress.getByName(host);
			final StringBuilder sb = new StringBuilder(host.length() + 16);
			final StringTokenizer st = new StringTokenizer(host, ".");
			final ArrayList<String> list = new ArrayList<String>();
			int levels = 2;
			while (st.hasMoreTokens()) {
				list.add(st.nextToken());
			}
			// Check IP addresses
			final String addr = inetAddr.getHostAddress();
			final String name = inetAddr.getHostName();
			if (addr.equals(name)) {
				if (inetAddr instanceof Inet4Address) {
					Collections.reverse(list);
					levels = 4;
				} else if (inetAddr instanceof Inet6Address) {
					throw new MalformedURLException("Unsupported IPv6");
				}
			}
			log.info("Domain tokens: " + list);
			while (true) {
				sb.setLength(0);
				getHostLevel(list, levels, sb);
				final String domCheck = sb.toString();
				if (levels == 2) {
					if (set2.contains(domCheck)) {
						levels++;
						continue;
					}
				} else if (levels == 3) {
					if (set3.contains(domCheck)) {
						levels++;
						continue;
					}
				}
				try {
					log.info("Checking SURBL(levels=" + levels + "): " + domCheck);
					if (InetAddress.getByName(sb.append(".multi.surbl.org.").toString()).getHostAddress()
							.startsWith("127.")) {
						log.info("SURBL checking (BANNED): " + domCheck);
						return true;
					}
				} catch (UnknownHostException ok) {
				}
				log.info("SURBL checking (CLEAN): " + domCheck);
				break;
			}
			return false;
		}

		private static void getHostLevel(final List<String> tokens, final int levels, final StringBuilder sb) {
			final int count = tokens.size();
			final int offset = count - levels;
			for (int i = 0; i < levels; i++) {
				sb.append(tokens.get(offset + i)).append('.');
			}
			sb.setLength(sb.length() - 1);
		}
	}

	static class PersistentStorage {
		private static final int BUF_LEN = 0x10000;
		private final KVStoreFactory<TokenHolder, MetaHolder> fac = new KVStoreFactory<TokenHolder, MetaHolder>(
				TokenHolder.class, MetaHolder.class);
		private final BplusTreeFile<TokenHolder, MetaHolder> map;
		private final FileStreamStore stream;
		private final ByteBuffer wbuf, rbuf;

		public PersistentStorage(final String storeDirName) throws InstantiationException,
				IllegalAccessException, IOException {
			final File storeDir = new File(storeDirName);
			if (!storeDir.exists()) {
				if (!storeDir.mkdirs())
					throw new IOException("Invalid storeDir: " + storeDirName);
			}
			final File storeTree = new File(storeDir, "tree");
			final File storeStream = new File(storeDir, "stream");
			wbuf = ByteBuffer.allocate(BUF_LEN);
			rbuf = ByteBuffer.allocate(BUF_LEN);
			map = fac.createTreeFile(fac.createTreeOptionsDefault()
					.set(KVStoreFactory.FILENAME, storeTree.getCanonicalPath())
					.set(KVStoreFactory.DISABLE_POPULATE_CACHE, true));
			stream = new FileStreamStore(storeStream, BUF_LEN);
			stream.setAlignBlocks(true);
			stream.setFlushOnWrite(true);
		}

		public void open() throws InvalidDataException {
			try {
				if (map.open())
					log.info("open tree ok");
			} catch (InvalidDataException e) {
				log.error("open tree error, recovery needed");
				if (map.recovery(false) && map.open()) {
					log.info("recovery ok, tree opened");
				} else {
					throw e;
				}
			}
			stream.open();
		}

		public void close() {
			stream.close();
			map.close();
		}

		public void put(final String k, final String v) {
			long offset = -1;
			synchronized (wbuf) {
				wbuf.clear();
				StringSerializer.fromStringToBuffer(wbuf, v);
				wbuf.flip();
				offset = stream.write(wbuf);
			}
			map.put(TokenHolder.valueOf(k), MetaHolder.valueOf(offset));
		}

		public void put(final String k, final MetaHolder meta) {
			map.put(TokenHolder.valueOf(k), meta);
		}

		public MetaHolder get(final String k) {
			final MetaHolder meta = map.get(TokenHolder.valueOf(k));
			if (meta == null)
				return null;
			synchronized (rbuf) {
				rbuf.clear();
				stream.read(meta.offset, rbuf);
				meta.url = StringSerializer.fromBufferToString(rbuf);
			}
			log.info("Found meta=" + meta);
			return meta;
		}

		public void remove(final String k) {
			map.remove(TokenHolder.valueOf(k));
		}
	}

	public static class TokenHolder extends DataHolder<TokenHolder> {
		private final String token;

		public TokenHolder() {
			this("");
		}

		public TokenHolder(final String token) {
			this.token = token;
		}

		public static TokenHolder valueOf(final String token) {
			return new TokenHolder(token);
		}

		@Override
		public int compareTo(final TokenHolder other) {
			return token.compareTo(other.token);
		}

		@Override
		public boolean equals(final Object other) {
			if (!(other instanceof TokenHolder))
				return false;
			return token.equals(((TokenHolder) other).token);
		}

		@Override
		public int hashCode() {
			return token.hashCode();
		}

		@Override
		public String toString() {
			return token;
		}

		@Override
		public int byteLength() {
			return 4 + KEY_SPACE + 1;
		}

		@Override
		public void serialize(final ByteBuffer bb) {
			StringSerializer.fromStringToBuffer(bb, token);
		}

		@Override
		public TokenHolder deserialize(final ByteBuffer bb) {
			return TokenHolder.valueOf(StringSerializer.fromBufferToString(bb));
		}
	}

	public static class MetaHolder extends DataHolder<MetaHolder> {
		public final long offset;
		public int timestamp;
		// Stored in Secondary Stream (pointed by offset)
		public String url = null;

		public MetaHolder() {
			this(0, 0);
		}

		public MetaHolder(final long offset, final int timestamp) {
			this.offset = offset;
			this.timestamp = timestamp;
		}

		public static MetaHolder valueOf(final long offset) {
			return new MetaHolder(offset, (int) (System.currentTimeMillis() / 1000));
		}

		@Override
		public int compareTo(final MetaHolder o) {
			if (offset < o.offset)
				return -1;
			if (offset > o.offset)
				return 1;
			if (timestamp < o.timestamp)
				return -1;
			if (timestamp > o.timestamp)
				return 1;
			return 0;
		}

		@Override
		public boolean equals(final Object other) {
			if (!(other instanceof MetaHolder))
				return false;
			return (compareTo((MetaHolder) other) == 0);
		}

		@Override
		public int hashCode() {
			return (int) (offset ^ (offset >>> 32));
		}

		@Override
		public String toString() {
			return "offset=" + offset + " timestamp=" + timestamp;
		}

		@Override
		public int byteLength() {
			return 8 + 4;
		}

		@Override
		public void serialize(final ByteBuffer bb) {
			bb.putLong(offset);
			bb.putInt(timestamp);
		}

		@Override
		public MetaHolder deserialize(final ByteBuffer bb) {
			return new MetaHolder(bb.getLong(), bb.getInt());
		}
	}

	public static class SpamDomainException extends MalformedURLException {
		private static final long serialVersionUID = 1L;

		public SpamDomainException(final String msg) {
			super(msg);
		}

		/**
		 * Speedup creation ignoring fillIn of stack trace
		 */
		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
}
