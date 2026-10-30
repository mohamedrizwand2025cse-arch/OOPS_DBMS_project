
package com.ruleweaver.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class MySqlConnectionFactory {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/ruleweaver_sql?serverTimezone=UTC";

    private MySqlConnectionFactory() {
        // Utility class
    }

    public static Connection getConnection() throws SQLException {
        String username = System.getenv("MYSQL_USER");
        String password = System.getenv("MYSQL_PASSWORD");
        String configuredUrl = System.getenv("MYSQL_URL");

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new SQLException(
                    "MySQL credentials are missing. Set MYSQL_USER "
                            + "and MYSQL_PASSWORD in the application environment.");
        }

        String url = configuredUrl == null || configuredUrl.isBlank()
                ? DEFAULT_URL
                : configuredUrl.trim();
        return DriverManager.getConnection(url, username, password);
    }
}
