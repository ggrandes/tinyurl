package org.javastack.tinyurl;

public class WhiteListNotFoundException extends TinyException {
	private static final long serialVersionUID = 42L;

	public WhiteListNotFoundException(final String msg) {
		super(msg);
	}
}