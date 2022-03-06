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
package org.javastack.tinyurl;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.javastack.kvstore.structures.btree.BplusTree.InvalidDataException;
import org.javastack.mapexpression.InvalidExpression;
import org.javastack.surbl.SURBL;

/**
 * Simple URL Shortener
 * 
 * @author Guillermo Grandes / guillermo.grandes[at]gmail.com
 */
public class TinyURL extends HttpServlet {
	static final Logger log = Logger.getLogger(TinyURL.class);
	private static final long serialVersionUID = 42L;
	//
	private static final String CFG_STORAGE = "storage.dir";
	private static final String CFG_DUMP_KEY = "dump.key";
	private static final String CFG_WHITELIST = "whitelist.file";
	private static final String CFG_FLAGS = "check.flags";
	private static final String CFG_CHECK_CACHE = "check.cache.millis";
	private static final String CFG_CONN_TIMEOUT = "connection.timeout.millis";
	private static final String CFG_READ_TIMEOUT = "read.timeout.millis";
	//
	private static final String DEF_CHECKS = "WHITELIST,CONNECTION";
	//
	private Config config;
	private String dumpKey = null;
	private Set<CheckType> checkFlags;
	private int connectionTimeout, readTimeout, checkCacheExpire;
	private Persistence store;
	private Hasher hasher;
	private SURBL surbl;
	private WhiteList whiteList;
	private LinkedHashMap<String, Integer> checkCache;

	@Override
	public void init() throws ServletException {
		try {
			init0();
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	private void init0() throws NoSuchAlgorithmException, InstantiationException, IllegalAccessException,
			IOException, InvalidExpression, InvalidDataException, ClassNotFoundException {
		// Config Source
		final String configSource = System.getProperty(Config.PROP_CONFIG, Config.DEF_CONFIG_FILE);
		log.info("ConfigSource: " + configSource);
		config = new Config(configSource);

		// Storage Directory
		final String defStoreDir = getServletContext().getRealPath("/WEB-INF/storage/");
		String storeDir = config.get(CFG_STORAGE);
		if (storeDir == null) {
			storeDir = defStoreDir;
			config.put(CFG_STORAGE, storeDir);
		}
		if (storeDir != null) {
			final File dir = new File(storeDir).getAbsoluteFile();
			if (!dir.exists()) {
				dir.mkdirs();
			}
			if (!dir.exists()) {
				log.error("StoragePath (NOT_FOUND): " + dir);
				throw new FileNotFoundException(CFG_STORAGE + " not found: " + dir);
			}
			log.info("StoragePath: " + dir);
		}
		connectionTimeout = Math.max(config.getInt(CFG_CONN_TIMEOUT, Constants.DEF_CONNECTION_TIMEOUT), 1000);
		readTimeout = Math.max(config.getInt(CFG_READ_TIMEOUT, Constants.DEF_READ_TIMEOUT), 1000);
		log.info("Timeouts connection=" + connectionTimeout + "ms read=" + readTimeout + "ms");

		// Dump Key
		dumpKey = config.get(CFG_DUMP_KEY);
		if (dumpKey == null) {
			dumpKey = generateRandomKey(64);
			log.info("Generated random dump.key=" + dumpKey);
			writeKey(new File(storeDir, "dump.key"), dumpKey);
		}

		// Check Flags
		checkFlags = CheckType.parseFlags(config.get(CFG_FLAGS, DEF_CHECKS));
		checkCacheExpire = (Math.max(config.getInt(CFG_CHECK_CACHE, Constants.DEF_CHECK_CACHE_EXPIRE), 1000) / 1000);
		log.info("Check flags=" + checkFlags + " cache=" + checkCacheExpire + "seconds");
		// Message Digester
		hasher = new Hasher();
		// WhiteList Check
		if (checkFlags.contains(CheckType.WHITELIST)) {
			// WhiteList File
			final String defWhiteListFile = "file:///"
					+ new File(storeDir, "whitelist.conf").getAbsolutePath();
			final String whiteListFile = config.get(CFG_WHITELIST, defWhiteListFile);
			log.info("WhiteListFile: " + whiteListFile);
			whiteList = new WhiteList(whiteListFile) //
					.setConnectionTimeout(connectionTimeout) //
					.setReadTimeout(readTimeout);
			whiteList.load();
		}
		// SURBL Check
		if (checkFlags.contains(CheckType.SURBL)) {
			surbl = new SURBL(storeDir) //
					.setConnectionTimeout(connectionTimeout) //
					.setReadTimeout(readTimeout);
			surbl.load();
		}
		// Storage
		try {
			final String defaultClass = PersistentKVStore.class.getName();
			final Class<?> clazz = Class.forName(config.get("storage.class", defaultClass));
			store = (Persistence) clazz.newInstance();
			log.info("Storage class=" + clazz.getName());
			store.configure(config.getSubview("storage"));
			store.open();
		} catch (IOException e) {
			closeSilent(store);
			throw e;
		}
		// Check cache
		if (!checkFlags.isEmpty()) {
			checkCache = new LinkedHashMap<String, Integer>() {
				private static final long serialVersionUID = 42L;

				@Override
				protected boolean removeEldestEntry(final Map.Entry<String, Integer> eldest) {
					return size() > 128;
				}
			};
		}
	}

	@Override
	public void destroy() {
		closeSilent(store);
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		try {
			MDC.put(Constants.MDC_IP, request.getRemoteAddr());
			MDC.put(Constants.MDC_ID, getNewID());
			doGet0(request, response);
		} finally {
			MDC.clear();
		}
	}

	private void doGet0(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		final String pathInfo = request.getPathInfo();
		if (dumpKey != null) {
			if (pathInfo.startsWith("/dump/")) {
				if (pathInfo.substring(6).equals(dumpKey)) {
					response.setContentType("text/csv; charset=ISO-8859-1");
					store.dump(response.getOutputStream());
					return;
				}
				final PrintWriter out = response.getWriter();
				sendError(response, out, HttpServletResponse.SC_FORBIDDEN, "Invalid Key");
				return;
			}
		}
		final String key = getPathInfoKey(pathInfo);
		if (key != null) {
			final TinyData meta = store.get(key);
			if (meta != null) {
				log.info("Found id=" + key + " url=" + meta.getURL());
				// Found - send response
				response.sendRedirect(meta.getURL());
				return;
			}
		}
		final PrintWriter out = response.getWriter();
		sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Not Found");
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		try {
			MDC.put(Constants.MDC_IP, request.getRemoteAddr());
			MDC.put(Constants.MDC_ID, getNewID());
			doPost0(request, response);
		} finally {
			MDC.clear();
		}
	}

	private void doPost0(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		final PrintWriter out = response.getWriter();
		final String url = request.getParameter("url");
		if ((url == null) || (url.length() < Constants.MIN_URL_LENGTH)) {
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL Parameter");
			return;
		}
		String key = hasher.hashURL(url);
		int collision = 0;
		while (true) { // Handle possible collisions
			final TinyData meta = store.get(key);
			// Dont exists
			if (meta == null)
				break;
			// Duplicated
			if (url.equals(meta.getURL())) {
				sendResponse(response, out, url, key, collision, false);
				return;
			}
			// Collision
			if (++collision > Constants.MAX_COLLISION) {
				log.error("Too many collisions { url=" + url + " id=" + key + " }");
				sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"ERROR: Unable to Short URL");
				return;
			}
			key = hasher.hashURL(Integer.toString(collision) + ":" + url);
		}
		// Check URL validity
		try {
			checkURL(new URL(url));
		} catch (IOException e) {
			log.error("Invalid URL: " + e);
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL ("
					+ e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
			return;
		} catch (Exception e) {
			log.error("Invalid URL: " + e, e);
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL ("
					+ e.getClass().getSimpleName() + ")");
			return;
		}
		// Store new URL
		store.put(key, url);
		sendResponse(response, out, url, key, collision, true);
	}

	private final void checkURL(final URL url) throws IOException {
		if (checkFlags.isEmpty())
			return;
		final int now = (int) (System.currentTimeMillis() / 1000);
		final Integer ts;
		synchronized (checkCache) {
			ts = checkCache.get(url.getHost());
		}
		boolean checkHost = true;
		if (ts != null) {
			final int cacheTs = Math.abs(ts.intValue());
			final boolean cacheNegative = (ts.intValue() < 0);
			if ((cacheTs + checkCacheExpire) > now) {
				if (cacheNegative) {
					throw new MalformedURLException("Invalid URL (Cache)");
				} else {
					checkHost = false; // Valid URL (Cache)
				}
			}
		}
		InputStream is = null;
		URLConnection conn = null;
		try {
			if (checkHost) {
				if ((whiteList != null) && !whiteList.checkWhiteList(url.getHost())) {
					synchronized (checkCache) {
						checkCache.put(url.getHost(), Integer.valueOf(-now));
					}
					throw new WhiteListNotFoundException("Domain not in WhiteList: " + url.getHost());
				}
				if ((surbl != null) && surbl.checkSURBL(url.getHost())) {
					synchronized (checkCache) {
						checkCache.put(url.getHost(), Integer.valueOf(-now));
					}
					throw new SpamDomainException("Spam domain detected: " + url.getHost());
				}
			}
			if (checkFlags.contains(CheckType.CONNECTION)) {
				conn = url.openConnection();
				conn.setConnectTimeout(connectionTimeout);
				conn.setReadTimeout(readTimeout);
				conn.setDoOutput(false);
				conn.setUseCaches(true);
				conn.connect();
				is = conn.getInputStream();
				byte[] buf = new byte[2048];
				while (is.read(buf) > 0) {
					continue;
				}
			}
			if (checkHost) {
				synchronized (checkCache) {
					checkCache.put(url.getHost(), Integer.valueOf((int) (System.currentTimeMillis() / 1000)));
				}
			}
		} finally {
			closeSilent(is);
		}
	}

	private static final String getNewID() {
		return UUID.randomUUID().toString();
	}

	private static final void sendResponse(final HttpServletResponse response, final PrintWriter out,
			final String url, final String key, final int collision, final boolean isNew) {
		final String res = "{ \"id\": \"" + key + "\" }";
		// Send Response
		response.setContentType("application/json");
		out.println(res);
		log.log((collision > 0 ? Level.WARN : Level.INFO), "Mapping url=" + url + " id=" + key
				+ " Response: " + res + " collition=" + collision + (isNew ? " (new)" : " (reuse)"));
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
		final int len = pathInfo.length();
		for (int i = 1; i < len; i++) {
			final char c = pathInfo.charAt(i);
			if ((c >= 'A') && (c <= 'Z'))
				continue;
			if ((c >= 'a') && (c <= 'z'))
				continue;
			if ((c >= '0') && (c <= '9'))
				continue;
			if ((c == '-') || (c == '_'))
				continue;
			log.warn("Invalid path: " + pathInfo);
			return null;
		}
		return pathInfo.substring(1);
	}

	private static final String generateRandomKey(final int len) throws UnsupportedEncodingException {
		final char[] alpha = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
		final SecureRandom r = new SecureRandom();
		final StringBuilder sb = new StringBuilder(len);
		final byte[] b = new byte[len];
		r.nextBytes(b);
		for (int i = 0; (i < b.length) && (sb.length() < len); i++) {
			final char c = alpha[(b[i] & 0x7F) % alpha.length];
			if (c >= '2' && c <= '9') {
				sb.append(c);
			} else if (c >= 'A' && c <= 'H') {
				sb.append(c);
			} else if (c >= 'J' && c <= 'N') {
				sb.append(c);
			} else if (c >= 'P' && c <= 'Z') {
				sb.append(c);
			} else if (c >= 'a' && c <= 'k') {
				sb.append(c);
			} else if (c >= 'm' && c <= 'z') {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static final void writeKey(final File f, final String key) throws IOException {
		FileWriter wr = null;
		try {
			wr = new FileWriter(f);
			wr.write(key);
		} finally {
			closeSilent(wr);
			f.setExecutable(false, false);
			f.setWritable(false, false);
			f.setReadable(false, false);
			f.setWritable(true, true);
			f.setReadable(true, true);
		}
	}

	private static final void closeSilent(final Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Throwable ign) {
			}
		}
	}
}
