package util;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;

public class DBConnection {

    private static BasicDataSource pool;

    public static Connection getConnection() throws SQLException {
        try {
            return dataSource().getConnection();
        } catch (IllegalStateException error) {
            throw new SQLException("Database configuration is incomplete or invalid.", error);
        }
    }

    private static synchronized BasicDataSource dataSource() {
        if (pool == null) {
            BasicDataSource configured = new BasicDataSource();
            configured.setDriverClassLoader(DBConnection.class.getClassLoader());
            configured.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            String url = AppConfig.get("DB_JDBC_URL");
            if (url.isEmpty()) {
                url = "jdbc:sqlserver://" + AppConfig.require("DB_HOST") + ":"
                        + AppConfig.integer("DB_PORT", 1, 65535) + ";databaseName=" + AppConfig.require("DB_NAME")
                        + ";encrypt=" + AppConfig.bool("DB_ENCRYPT")
                        + ";trustServerCertificate=" + AppConfig.bool("DB_TRUST_SERVER_CERTIFICATE")
                        + ";loginTimeout=" + AppConfig.integer("DB_LOGIN_TIMEOUT_SECONDS", 1, 300) + ";";
            }
            configured.setUrl(url);
            configured.setUsername(AppConfig.require("DB_USER"));
            configured.setPassword(AppConfig.require("DB_PASSWORD"));
            configured.setInitialSize(AppConfig.integer("DB_POOL_MIN_IDLE", 0, 1000));
            configured.setMinIdle(AppConfig.integer("DB_POOL_MIN_IDLE", 0, 1000));
            configured.setMaxIdle(AppConfig.integer("DB_POOL_MAX_IDLE", 1, 1000));
            configured.setMaxTotal(AppConfig.integer("DB_POOL_MAX_TOTAL", 1, 1000));
            configured.setMaxWait(Duration.ofSeconds(AppConfig.integer("DB_POOL_WAIT_SECONDS", 1, 300)));
            configured.setDefaultAutoCommit(true);
            configured.setDefaultReadOnly(false);
            configured.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            // Returning a connection must never commit an unfinished checkout.
            configured.setRollbackOnReturn(true);
            configured.setAutoCommitOnReturn(true);
            configured.setTestOnBorrow(true);
            configured.setValidationQuery("SELECT 1");
            configured.setValidationQueryTimeout(Duration.ofSeconds(AppConfig.integer("DB_VALIDATION_TIMEOUT_SECONDS", 1, 300)));
            configured.setTestWhileIdle(true);
            configured.setDurationBetweenEvictionRuns(Duration.ofSeconds(AppConfig.integer("DB_EVICTION_SECONDS", 1, 3600)));
            pool = configured;
        }
        return pool;
    }

    public static synchronized void shutdown() throws SQLException {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }
}
