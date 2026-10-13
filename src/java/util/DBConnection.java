package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_INSTANCE = "SQLEXPRESS1";
    private static final String DEFAULT_DATABASE = "MenWearStore";
    private static final String DEFAULT_USER = "sa";
    private static final String DEFAULT_PASSWORD = "123456";

    private static final String DRIVER =
            "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    public static Connection getConnection() throws SQLException {

        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "Microsoft SQL Server JDBC driver was not found. "
                    + "Add the mssql-jdbc JAR to the application classpath.",
                    e
            );
        }

        try {
            return DriverManager.getConnection(
                    config.url,
                    config.user,
                    config.password
            );
        } catch (SQLException e) {
            throw new SQLException(
                    "Could not connect to SQL Server at " + config.target
                    + " (database " + config.database + "). Check the server, "
                    + "instance or port, database name, and SQL credentials.",
                    e.getSQLState(),
                    e.getErrorCode(),
                    e
            );
        }
    }

    /**
     * Connection settings can be overridden with JVM properties or
     * environment variables. JVM properties take precedence. If a port is
     * supplied, it is used instead of the named instance.
     *
     * JVM properties: menwear.db.host, menwear.db.port,
     * menwear.db.instance, menwear.db.name, menwear.db.user,
     * menwear.db.password.
     * Environment variables use the MENWEAR_DB_ prefix.
     */
    private static final class DatabaseConfig {

        private final String url;
        private final String user;
        private final String password;
        private final String database;
        private final String target;

        private DatabaseConfig(String url, String user, String password,
                               String database, String target) {
            this.url = url;
            this.user = user;
            this.password = password;
            this.database = database;
            this.target = target;
        }

        private static DatabaseConfig fromEnvironment() throws SQLException {
            String host = setting("menwear.db.host", "MENWEAR_DB_HOST",
                    DEFAULT_HOST);
            String database = setting("menwear.db.name", "MENWEAR_DB_NAME",
                    DEFAULT_DATABASE);
            String user = setting("menwear.db.user", "MENWEAR_DB_USER",
                    DEFAULT_USER);
            String password = setting("menwear.db.password",
                    "MENWEAR_DB_PASSWORD", DEFAULT_PASSWORD);
            String port = setting("menwear.db.port", "MENWEAR_DB_PORT", "");
            String instance = setting("menwear.db.instance",
                    "MENWEAR_DB_INSTANCE", DEFAULT_INSTANCE);

            requireSafeValue("host", host);
            requireSafeValue("database", database);

            StringBuilder url = new StringBuilder("jdbc:sqlserver://")
                    .append(host);
            String target = host;

            if (!port.isEmpty()) {
                int portNumber;
                try {
                    portNumber = Integer.parseInt(port);
                } catch (NumberFormatException e) {
                    throw new SQLException(
                            "MENWEAR_DB_PORT must be a number from 1 to 65535.",
                            e
                    );
                }
                if (portNumber < 1 || portNumber > 65535) {
                    throw new SQLException(
                            "MENWEAR_DB_PORT must be a number from 1 to 65535."
                    );
                }
                url.append(':').append(portNumber);
                target += ":" + portNumber;
            } else if (!instance.isEmpty()) {
                requireSafeValue("instance", instance);
                url.append(";instanceName=").append(instance);
                target += "\\" + instance;
            }

            url.append(";databaseName=").append(database)
                    .append(";encrypt=true")
                    .append(";trustServerCertificate=true")
                    .append(";loginTimeout=15");

            return new DatabaseConfig(url.toString(), user, password,
                    database, target);
        }

        private static String setting(String property, String environment,
                                      String defaultValue) {
            String value = System.getProperty(property);
            if (value == null || value.trim().isEmpty()) {
                value = System.getenv(environment);
            }
            return value == null || value.trim().isEmpty()
                    ? defaultValue : value.trim();
        }

        private static void requireSafeValue(String name, String value)
                throws SQLException {
            if (value == null || value.isEmpty() || value.contains(";")) {
                throw new SQLException("Invalid SQL Server " + name + " setting.");
            }
        }
    }
}
