package org.javastack.tinyurl.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.log4j.Logger;

/**
 * Helper Factory for instanciate DBCP Pooled DataSources
 */
public class DataSourceFactory {
	private static final Logger log = Logger.getLogger(DataSourceFactory.class);

	/**
	 * Return default properties
	 * <a href="https://tomcat.apache.org/tomcat-8.5-doc/jdbc-pool.html#How_to_use">tomcat dbcp</a>
	 */
	public static Properties defaultProperties() {
		final Properties prop = new Properties();
		// prop.setProperty("driverClassName", "org.h2.Driver");
		// prop.setProperty("url", "jdbc:h2:tcp://localhost:9092/test;MODE=MYSQL;LOCK_TIMEOUT=50000");
		// prop.setProperty("username", "sa");
		// prop.setProperty("password", "");
		prop.setProperty("maxActive", "1");
		prop.setProperty("minIdle", "0");
		prop.setProperty("maxIdle", "1");
		prop.setProperty("initialSize", "1");
		prop.setProperty("testOnBorrow", "true");
		prop.setProperty("validationQuery", "SELECT 1 FROM DUAL");
		prop.setProperty("removeAbandoned", "true");
		prop.setProperty("removeAbandonedTimeout", "300");
		prop.setProperty("logAbandoned", "true");
		prop.setProperty("closeMethod", "close");
		prop.setProperty("maxWait", "-1");
		return prop;
	}

	/**
	 * Like JNDI, you can create DataSource
	 * 
	 * @param prop properties
	 * @return
	 * @throws Exception
	 * 
	 * @see {@link #destroyDataSource(DataSource)}
	 */
	public static DataSource createDataSource(final Properties prop) throws Exception {
		return new org.apache.tomcat.jdbc.pool.DataSourceFactory().createDataSource(prop);
	}

	/**
	 * Closes and releases all idle connections that are currently stored in
	 * the connection pool associated with this data source.
	 * 
	 * Connections that are checked out to clients when this method is invoked
	 * are not affected. When client applications subsequently invoke
	 * Connection.close() to return these connections to the pool, the
	 * underlying JDBC connections are closed.
	 * 
	 * Attempts to acquire connections using getConnection() after this method
	 * has been invoked result in SQLExceptions.
	 * 
	 * @param dataSource
	 * @throws Exception
	 */
	public static void destroyDataSource(final DataSource dataSource) {
		if (dataSource != null) {
			try {
				((org.apache.tomcat.jdbc.pool.DataSource) dataSource).close();
			} catch (Exception e) {
				log.error("Unable to destroyDataSource(" + dataSource + "): " + e.toString(), e);
			}
		}
	}

	/**
	 * Simple Test
	 */
	public static void main(final String[] args) throws Throwable {
		final Properties test = defaultProperties();
		test.setProperty("driverClassName", "org.h2.Driver");
		test.setProperty("url", "jdbc:h2:mem:test_rapid;MODE=MYSQL");
		test.setProperty("username", "sa");
		test.setProperty("password", "");
		final DataSource dataSource = createDataSource(test);
		final Connection conn = dataSource.getConnection();
		final PreparedStatement pstmt = conn.prepareStatement("SELECT 1 AS id FROM DUAL");
		final ResultSet rset = pstmt.executeQuery();
		if (rset.next()) {
			System.out.println("id=" + rset.getInt("id"));
		}
		rset.close();
		pstmt.close();
		conn.close();
		destroyDataSource(dataSource);
	}
}
