package org.javastack.tinyurl;

import java.io.Closeable;
import java.io.IOException;

interface Persistence extends Closeable {
	/**
	 * Open Storage
	 * 
	 * @throws IOException
	 */
	public void open() throws IOException;

	/**
	 * Close Storage
	 */
	public void close();

	/**
	 * Put Key and Url in Storage
	 * 
	 * @param key primary and unique for search
	 * @param url data to store
	 * @throws IOException
	 */
	public void put(final String key, final String url) throws IOException;

	/**
	 * Get Url from Storage, key is user for search
	 * 
	 * @param key primary and unique
	 * @return
	 * @throws IOException
	 */
	public TinyData get(final String key) throws IOException;

	/**
	 * Remote Key from Storage
	 * 
	 * @param key primary and unique
	 * @throws IOException
	 */
	public void remove(final String key) throws IOException;
}