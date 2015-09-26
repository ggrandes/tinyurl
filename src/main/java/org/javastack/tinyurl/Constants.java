package org.javastack.tinyurl;

class Constants {
	public static final String MDC_IP = "IP";
	public static final String MDC_ID = "ID";

	public static final int DEF_CONNECTION_TIMEOUT = 10000; // millis
	public static final int DEF_READ_TIMEOUT = 30000; // millis
	public static final int DEF_CHECK_CACHE_EXPIRE = 60000; // millis

	public static final int MIN_URL_LENGTH = 12;
	public static final int KEY_SPACE = 6;
	public static final int MAX_COLLISION = 5;
}
