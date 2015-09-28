package org.javastack.tinyurl.jdbc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.javastack.stringproperties.StringProperties;
import org.javastack.tinyurl.Persistence;
import org.javastack.tinyurl.TinyData;

public class PersistentMySQL implements Persistence {
	private static final Logger log = Logger.getLogger(PersistentMySQL.class);
	private static final String table = "mapping";
	private static final String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + table + " (" + //
			"token VARCHAR(22) CHARACTER SET latin1 COLLATE latin1_general_cs NOT NULL," + //
			"url VARCHAR(65000) NOT NULL," + //
			"timestamp INT(11) unsigned NOT NULL," + //
			"PRIMARY KEY (token)" + //
			") ENGINE=InnoDB;";
	private Properties config = null;
	private DataSource dataSource = null;

	public PersistentMySQL() {
	}

	@Override
	public void configure(final StringProperties config) {
		this.config = DataSourceFactory.defaultProperties();
		this.config.setProperty("driverClassName", "com.mysql.jdbc.Driver");
		for (final String key : config.stringPropertyNames()) {
			final String value = config.getProperty(key);
			this.config.setProperty(key, value);
		}
		log.info("Storage config=" + this.config);
	}

	@Override
	public void open() throws IOException {
		try {
			dataSource = DataSourceFactory.createDataSource(config);
		} catch (Exception e) {
			close();
			throw new IOException(e);
		}
		Connection conn = null;
		PreparedStatement pstmtCreate = null;
		try {
			conn = dataSource.getConnection();
			pstmtCreate = conn.prepareStatement(TABLE_CREATE);
			pstmtCreate.executeUpdate();
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			closeSilent(pstmtCreate);
			closeSilent(conn);
		}
	}

	@Override
	public void close() {
		DataSourceFactory.destroyDataSource(dataSource);
	}

	@Override
	public void put(final String key, final String url) throws IOException {
		Connection conn = null;
		PreparedStatement pstmtPut = null;
		try {
			conn = dataSource.getConnection();
			pstmtPut = conn.prepareStatement("REPLACE INTO " + table
					+ " (token, url, timestamp) VALUES(?, ?, ?)");
			pstmtPut.setString(1, key);
			pstmtPut.setString(2, url);
			pstmtPut.setInt(3, (int) (System.currentTimeMillis() / 1000));
			pstmtPut.executeUpdate();
		} catch (SQLException e) {
			throw new IOException(e);
		} finally {
			closeSilent(pstmtPut);
			closeSilent(conn);
		}
	}

	@Override
	public TinyData get(final String key) throws IOException {
		Connection conn = null;
		PreparedStatement pstmtGet = null;
		ResultSet rset = null;
		try {
			conn = dataSource.getConnection();
			pstmtGet = conn.prepareStatement("SELECT url FROM " + table + " WHERE token = ?");
			pstmtGet.setString(1, key);
			rset = pstmtGet.executeQuery();
			if (rset.next()) {
				final String url = rset.getString("url");
				return new TinyData() {
					@Override
					public String getURL() {
						return url;
					}
				};
			}
		} catch (SQLException e) {
			throw new IOException(e);
		} finally {
			closeSilent(rset);
			closeSilent(pstmtGet);
			closeSilent(conn);
		}
		return null;
	}

	@Override
	public void remove(final String key) throws IOException {
		Connection conn = null;
		PreparedStatement pstmtRemove = null;
		try {
			conn = dataSource.getConnection();
			pstmtRemove = conn.prepareStatement("DELETE FROM " + table + " WHERE token = ?");
			pstmtRemove.setString(1, key);
			pstmtRemove.executeUpdate();
		} catch (SQLException e) {
			throw new IOException(e);
		} finally {
			closeSilent(pstmtRemove);
			closeSilent(conn);
		}
	}

	@Override
	public void dump(final OutputStream out) throws IOException {
		Connection conn = null;
		PreparedStatement pstmtDump = null;
		ResultSet rset = null;
		try {
			conn = dataSource.getConnection();
			pstmtDump = conn.prepareStatement("SELECT token, url, timestamp FROM " + table);
			rset = pstmtDump.executeQuery();
			final Charset iso = Charset.forName("ISO-8859-1");
			final byte[] CRLF = "\r\n".getBytes(iso);
			out.write("token,url,created-unix-epoch-utc".getBytes(iso));
			out.write(CRLF);
			while (rset.next()) {
				final String token = rset.getString("token");
				final String url = rset.getString("url");
				final int timestamp = rset.getInt("timestamp");
				out.write(token.getBytes(iso));
				out.write(',');
				out.write(url.getBytes(iso));
				out.write(',');
				out.write(Long.toString(timestamp).getBytes(iso));
				out.write(CRLF);
			}
			out.flush();
		} catch (SQLException e) {
			throw new IOException(e);
		} finally {
			closeSilent(rset);
			closeSilent(pstmtDump);
			closeSilent(conn);
		}
	}

	private static final void closeSilent(final AutoCloseable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Throwable ign) {
			}
		}
	}
}
