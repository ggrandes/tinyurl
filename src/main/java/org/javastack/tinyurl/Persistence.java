package org.javastack.tinyurl;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import org.javastack.stringproperties.StringProperties;

public interface Persistence extends Closeable {
	/**
	 * Set configuration for Persistence
	 * 
	 * @param properties configuration
	 */
	public void configure(final StringProperties properties);

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

	/**
	 * Dump storage in (<a href="https://tools.ietf.org/html/rfc4180#page-2">RFC-4180</a> type 3) CSV format.
	 * <ul>
	 * <li>coma as field separator</il>
	 * <li>CR+LF as line separator</il>
	 * <li>encoding US-ASCII or ISO-8859-1</li>
	 * <li>with header with field names</li>
	 * </ul>
	 * <p>
	 * <code>key header,url header[,options header,...]\r\n</code><br/>
	 * <code>key1,url1[,options1,...]\r\n</code><br/>
	 * <code>key2,url2[,options2,...]\r\n</code>
	 * 
	 * @param out
	 * @throws IOException
	 */
	public void dump(final OutputStream out) throws IOException;
}