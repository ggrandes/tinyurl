package org.javastack.tinyurl;

import java.net.MalformedURLException;

public class TinyException extends MalformedURLException {
	private static final long serialVersionUID = 42L;

	public TinyException(final String msg) {
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
