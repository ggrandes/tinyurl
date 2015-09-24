package org.javastack.tinyurl;

import java.io.Closeable;
import java.io.IOException;

interface Persistence extends Closeable {
	public void open() throws IOException;

	public void close();

	public void put(final String key, final String url) throws IOException;

	public TinyData get(final String key) throws IOException;

	public void remove(final String key) throws IOException;
}