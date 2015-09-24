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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
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
	private static final String CFG_WHITELIST = "whitelist.file";
	private static final String CFG_FLAGS = "check.flags";
	private static final String CFG_CONN_TIMEOUT = "connection.timeout.millis";
	private static final String CFG_READ_TIMEOUT = "read.timeout.millis";
	//
	private static final String DEF_CHECKS = "WHITELIST,SURBL,CONNECTION";
	//
	private Config config;
	private Set<CheckType> checkFlags;
	private int connectionTimeout, readTimeout;
	private Persistence store;
	private Hasher hasher;
	private SURBL surbl;
	private WhiteList whiteList;

	@Override
	public void init() throws ServletException {
		try {
			init0();
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	private void init0() throws NoSuchAlgorithmException, InstantiationException, IllegalAccessException,
			IOException, InvalidExpression, InvalidDataException {
		// Config Source
		final String configSource = System.getProperty(Config.PROP_CONFIG, Config.DEF_CONFIG_FILE);
		log.info("ConfigSource: " + configSource);
		config = new Config(configSource);

		// Storage Directory
		final String defStoreDir = getServletContext().getRealPath("/WEB-INF/storage/");
		final String storeDir = config.get(CFG_STORAGE, defStoreDir);
		log.info("StoragePath: " + storeDir);
		connectionTimeout = config.getInt(CFG_CONN_TIMEOUT, Constants.DEF_CONNECTION_TIMEOUT);
		readTimeout = config.getInt(CFG_READ_TIMEOUT, Constants.DEF_READ_TIMEOUT);
		log.info("Timeouts connection=" + connectionTimeout + "ms read=" + readTimeout + "ms");

		// Check Flags
		checkFlags = CheckType.parseFlags(config.get(CFG_FLAGS, DEF_CHECKS));
		log.info("Check flags=" + checkFlags);
		// Message Digester
		hasher = new Hasher();
		// WhiteList Check
		if (checkFlags.contains(CheckType.WHITELIST)) {
			// WhiteList File
			final String defWhiteListFile = new File(storeDir, "whitelist.conf").getAbsolutePath();
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
			store = new PersistentStorage(storeDir);
			store.open();
		} catch (IOException e) {
			closeSilent(store);
			throw e;
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
		final PrintWriter out = response.getWriter();
		final String key = getPathInfoKey(request.getPathInfo());
		if (key != null) {
			final TinyData meta = store.get(key);
			if (meta != null) {
				log.info("Found id=" + key + " url=" + meta.getURL());
				// Found - send response
				response.sendRedirect(meta.getURL());
				return;
			}
		}
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
			checkURL(url);
		} catch (TinyException e) {
			log.error("Invalid URL: " + e);
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL Parameter (" + e + ")");
			return;
		} catch (Exception e) {
			log.error("Invalid URL: " + e, e);
			sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL Parameter ("
					+ e.getClass().getSimpleName() + ")");
			return;
		}
		// Store new URL
		store.put(key, url);
		sendResponse(response, out, url, key, collision, true);
	}

	private final void checkURL(final String url) throws IOException {
		if (checkFlags.isEmpty())
			return;
		InputStream is = null;
		URLConnection conn = null;
		try {
			final URL urlCheck = new URL(url);
			if ((whiteList != null) && !whiteList.checkWhiteList(urlCheck.getHost())) {
				throw new WhiteListNotFoundException("Domain not in WhiteList");
			}
			if ((surbl != null) && surbl.checkSURBL(urlCheck.getHost())) {
				throw new SpamDomainException("Spam domain detected");
			}
			if (checkFlags.contains(CheckType.CONNECTION)) {
				conn = urlCheck.openConnection();
				conn.setConnectTimeout(connectionTimeout);
				conn.setReadTimeout(readTimeout);
				conn.setDoOutput(false);
				conn.setUseCaches(true);
				if (conn instanceof HttpURLConnection) {
					((HttpURLConnection) conn).setRequestMethod("HEAD");
				}
				conn.connect();
				is = conn.getInputStream();
				byte[] buf = new byte[2048];
				while (is.read(buf) > 0) {
					continue;
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

	private static final void closeSilent(final Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Throwable ign) {
			}
		}
	}
}
