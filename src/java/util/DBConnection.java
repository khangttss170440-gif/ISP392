package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DB_NAME = "MenWearStore";
    private static final String DB_USER_NAME = "sa";
    private static final String DB_PASSWORD = "12345";

    private static final String DB_URL
        = "jdbc:sqlserver://localhost:1433;"
        + "databaseName=" + DB_NAME + ";"
        + "encrypt=true;"
        + "trustServerCertificate=true;"
        + "loginTimeout=15;";
    
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "Microsoft SQL Server JDBC driver is missing.",
                    e
            );
        }

        return DriverManager.getConnection(
                DB_URL,
                DB_USER_NAME,
                DB_PASSWORD
        );
    }
}
