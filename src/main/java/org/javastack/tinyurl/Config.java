package org.javastack.tinyurl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.log4j.Logger;
import org.javastack.mapexpression.InvalidExpression;
import org.javastack.stringproperties.StringProperties;

public class Config {
	static final Logger log = Logger.getLogger(Config.class);
	private static final String PACKAGE = Config.class.getPackage().getName();
	public static final String PROP_CONFIG = PACKAGE + ".config";
	public static final String DEF_CONFIG_FILE = PACKAGE + ".properties";

	private final StringProperties cfg;

	public Config(final String config) throws IOException {
		cfg = new StringProperties();
		InputStream is = null;
		try {
			is = loadFile(config);
			if (is != null) {
				cfg.getRootView().load(is);
			}
		} finally {
			closeSilent(is);
		}
	}

	public void put(final String key, final String value) {
		cfg.setProperty(key, value);
	}

	public StringProperties getSubview(final String prefix) {
		return cfg.getSubView(prefix);
	}

	public String get(final String key) throws InvalidExpression {
		return cfg.getPropertyEval(key);
	}

	public String get(final String key, final String def) throws InvalidExpression {
		return cfg.getPropertyEval(key, def);
	}

	public int getInt(final String key, final int def) throws InvalidExpression {
		final String value = get(key);
		if (value == null)
			return def;
		return Integer.parseInt(value);
	}

	public boolean getBoolean(final String key, final Boolean def) throws InvalidExpression {
		final String value = get(key);
		if (value == null)
			return def;
		return Boolean.parseBoolean(value);
	}

	private InputStream loadFile(final String fileName) throws IOException {
		InputStream is = null;
		if (fileName.startsWith("http:") || fileName.startsWith("https:") || fileName.startsWith("file:")) {
			is = new URL(fileName).openStream();
			if (is == null) {
				log.error("Unable to read config file (URL): " + fileName);
			}
		} else {
			is = getClass().getResourceAsStream("/" + fileName);
			if (is == null) {
				log.error("Unable to read config file (classpath): " + fileName);
			}
		}
		return is;
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
