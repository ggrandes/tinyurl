package org.javastack.tinyurl;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.javastack.packer.Base64;

public class Hasher {
	private static final Charset iso = Charset.forName("ISO-8859-1");
	private final MessageDigest md;

	public Hasher() throws NoSuchAlgorithmException {
		md = MessageDigest.getInstance("MD5");
	}

	public String hashURL(final String url) {
		byte[] b = url.getBytes(iso);
		synchronized (md) {
			b = md.digest(b);
		}
		return new String(Base64.encode(b, true), iso).substring(0, Constants.KEY_SPACE);
	}
}
