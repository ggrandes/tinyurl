package org.javastack.tinyurl;

public class SpamDomainException extends TinyException {
	private static final long serialVersionUID = 42L;

	public SpamDomainException(final String msg) {
		super(msg);
	}
}