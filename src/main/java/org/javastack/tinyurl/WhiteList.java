package org.javastack.tinyurl;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class WhiteList {
	private static final Logger log = Logger.getLogger(WhiteList.class);

	private final String whiteListFile;

	private volatile List<String> list = null;
	private long lastReload = 0;

	private int connectionTimeout = Constants.DEF_CONNECTION_TIMEOUT;
	private int readTimeout = Constants.DEF_READ_TIMEOUT;

	public WhiteList(final String whiteListFile) throws IOException {
		this.whiteListFile = whiteListFile;
	}

	public WhiteList setConnectionTimeout(final int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
		return this;
	}

	public WhiteList setReadTimeout(final int readTimeout) {
		this.readTimeout = readTimeout;
		return this;
	}

	public boolean load() throws IOException {
		InputStream is = null;
		BufferedReader in = null;
		try {
			is = loadFile(whiteListFile);
			if (is == null) {
				this.lastReload = System.currentTimeMillis();
				return false;
			}
			in = new BufferedReader(new InputStreamReader(is));
			String line = null;
			final ArrayList<String> list = new ArrayList<String>();
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				if (line.startsWith("#"))
					continue;
				list.add(line.toLowerCase());
			}
			log.info("Loaded " + list.size() + " Domains from " + whiteListFile);
			this.list = list;
			this.lastReload = System.currentTimeMillis();
			return true;
		} finally {
			closeSilent(in);
			closeSilent(is);
		}
	}

	private final InputStream loadFile(final String fileName) throws IOException {
		URL url = null;
		InputStream is = null;
		if (fileName.startsWith("http:") || fileName.startsWith("https:") || fileName.startsWith("file:")) {
			url = new URL(fileName);
		} else {
			url = getClass().getResource("/" + fileName);
		}
		if (url == null) {
			throw new FileNotFoundException("Not found: " + fileName);
		}
		try {
			final URLConnection conn = url.openConnection();
			conn.setConnectTimeout(connectionTimeout);
			conn.setReadTimeout(readTimeout);
			conn.setUseCaches(false);
			final long lastModified = conn.getLastModified(); // Can be 0
			is = conn.getInputStream();
			if (lastModified < lastReload) {
				closeSilent(is);
				return null;
			}
		} catch (Exception e) {
			log.error("Load WhiteList error: " + e, e);
		}
		return is;
	}

	public boolean checkWhiteList(final String domain) {
		if ((list != null) && list.isEmpty()) {
			return false;
		}
		if ((lastReload + Constants.DEF_WHITELIST_RELOAD) < System.currentTimeMillis()) {
			try {
				load();
			} catch (Exception e) {
				log.warn("Fail to reload Whitelist: " + whiteListFile);
			}
		}
		if ((list == null) || list.isEmpty()) {
			return false;
		}
		final String dd = domain.trim().toLowerCase();
		final int len = list.size();
		for (int i = 0; i < len; i++) {
			final String d = list.get(i);
			if (d.charAt(0) == '.') {
				if (dd.endsWith(d) || dd.equals(d.substring(1))) {
					return true;
				}
			} else {
				if (dd.equals(d)) {
					return true;
				}
			}
		}
		return false;
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
