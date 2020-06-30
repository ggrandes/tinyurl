/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.javastack.tinyurl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.javastack.mapexpression.InvalidExpression;
import org.javastack.qr.BarcodeFormat;
import org.javastack.qr.BitMatrix;
import org.javastack.qr.EncodeHintType;
import org.javastack.qr.ErrorCorrectionLevel;
import org.javastack.qr.MatrixToImageWriter;
import org.javastack.qr.QRCodeWriter;
import org.javastack.qr.WriterException;

/**
 * Simple QR Generation
 * 
 * @author Guillermo Grandes / guillermo.grandes[at]gmail.com
 */
public class TinyQR extends HttpServlet {
	static final Logger log = Logger.getLogger(TinyQR.class);
	private static final long serialVersionUID = 42L;
	private static final String BASE_URI_TINYURL = "/r/"; // defined in web.xml
	//
	private static final String CFG_BASE_URL = "base.url"; // https://tiny.javastack.org/r/
	private static final String CFG_QR_SIZE_MIN = "qr.size.min";
	private static final String CFG_QR_SIZE_MAX = "qr.size.max";
	private static final String CFG_QR_SIZE_DEFAULT = "qr.size.default";
	//
	private Config config;
	private String baseURL;
	private int qrSizeMin, qrSizeMax, qrSizeDefault;
	private LinkedHashMap<String, byte[]> qrCache;

	@Override
	public void init() throws ServletException {
		try {
			init0();
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	private void init0() throws IOException, InvalidExpression {
		// Config Source
		final String configSource = System.getProperty(Config.PROP_CONFIG, Config.DEF_CONFIG_FILE);
		log.info("ConfigSource: " + configSource);
		config = new Config(configSource);
		baseURL = config.get(CFG_BASE_URL);
		qrSizeMin = Math.max(config.getInt(CFG_QR_SIZE_MIN, Constants.DEF_QR_SIZE_MIN), 50);
		qrSizeMax = Math.min(config.getInt(CFG_QR_SIZE_MAX, Constants.DEF_QR_SIZE_MAX), 2000);
		qrSizeDefault = config.getInt(CFG_QR_SIZE_DEFAULT, Constants.DEF_QR_SIZE);
		if ((baseURL == null) || baseURL.isEmpty()) {
			log.warn("baseURL: undefined");
		} else {
			log.info("baseURL: " + baseURL);
		}
		// Init cache
		qrCache = new LinkedHashMap<String, byte[]>() {
			private static final long serialVersionUID = 42L;

			@Override
			protected boolean removeEldestEntry(final Map.Entry<String, byte[]> eldest) {
				return size() > 128;
			}
		};
	}

	@Override
	public void destroy() {
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		try {
			MDC.put(Constants.MDC_IP, request.getRemoteAddr());
			MDC.put(Constants.MDC_ID, getNewID());
			doGet0(request, response);
		} finally {
			MDC.clear();
		}
	}

	private void doGet0(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {
		final int size = Math.min(qrSizeMax, Math.max(qrSizeMin, //
				parseInt(request.getParameter("size"), qrSizeDefault)));
		final String pathInfo = request.getPathInfo();
		final String key = getPathInfoKey(pathInfo);
		if (key != null) {
			final String cacheKey = key + ":" + size;
			byte[] qr = null;
			synchronized (qrCache) {
				qr = qrCache.get(cacheKey);
			}
			if (qr != null) {
				log.info("QR cache found id=" + key + " size=" + qr.length);
			} else {
				final String urlBase = getBaseURL(request);
				final String input = urlBase + key;
				final long begin = System.currentTimeMillis();
				qr = generateQR(input, size);
				synchronized (qrCache) {
					qrCache.put(cacheKey, qr);
				}
				log.info("QR generated (" + (System.currentTimeMillis() - begin) + "ms)" //
						+ " pixels=" + size + " length=" + qr.length + " input=" + input);
			}
			// Send response
			sendResponse(response, qr);
			return;
		}
		final PrintWriter out = response.getWriter();
		sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Not Found");
	}

	private static final byte[] generateQR(final String input, final int size) throws IOException {
		final Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
		hints.put(EncodeHintType.MARGIN, 1);
		hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); // Default ISO-8859-1
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);

		try {
			final BitMatrix matrix = new QRCodeWriter().encode(input, //
					BarcodeFormat.QR_CODE, //
					size, size, // 300x300
					hints);
			final ByteArrayOutputStream imageOut = new ByteArrayOutputStream(1024);
			MatrixToImageWriter.writeToStream(matrix, "PNG", imageOut);
			return imageOut.toByteArray();
		} catch (WriterException e) {
			throw new IOException(e);
		}
	}

	private static final String getNewID() {
		return UUID.randomUUID().toString();
	}

	private static final void sendResponse(final HttpServletResponse response, final byte[] qr)
			throws IOException {
		// Send Response
		response.setContentType("image/png");
		response.setContentLength(qr.length);
		response.setHeader("Cache-Control", "public");
		response.getOutputStream().write(qr);
	}

	private static final void sendError(final HttpServletResponse response, final PrintWriter out,
			final int status, final String msg) {
		response.setContentType("text/plain; charset=ISO-8859-1");
		response.setStatus(status);
		out.println(msg);
	}

	private final String getBaseURL(final HttpServletRequest request) {
		if ((baseURL != null) && !baseURL.isEmpty()) {
			return baseURL;
		}
		final StringBuffer urlBase = request.getRequestURL();
		final int l1 = request.getServletPath().length();
		final int l2 = request.getPathInfo().length();
		urlBase.setLength(urlBase.length() - l1 - l2);
		urlBase.append(BASE_URI_TINYURL);
		return urlBase.toString();
	}

	private static final int parseInt(final String in, final int def) {
		try {
			if ((in != null) && !in.isEmpty()) {
				return Integer.parseInt(in);
			}
		} catch (Exception ign) {
		}
		return def;
	}

	private static final String getPathInfoKey(final String pathInfo) {
		if (pathInfo == null)
			return null;
		if (pathInfo.isEmpty())
			return null;
		final int len = pathInfo.length();
		for (int i = 1; i < len; i++) {
			final char c = pathInfo.charAt(i);
			if ((c >= 'A') && (c <= 'Z'))
				continue;
			if ((c >= 'a') && (c <= 'z'))
				continue;
			if ((c >= '0') && (c <= '9'))
				continue;
			if ((c == '-') || (c == '_'))
				continue;
			log.warn("Invalid path: " + pathInfo);
			return null;
		}
		return pathInfo.substring(1);
	}
}
