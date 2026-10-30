
package com.ruleweaver;

import com.ruleweaver.config.MySqlConnectionFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class MySqlConnectionTest {

    public static void main(String[] args) {
        String user = System.getenv("MYSQL_USER");
        String password = System.getenv("MYSQL_PASSWORD");

        if (user == null || password == null) {
            System.err.println(
                    "Set MYSQL_USER and MYSQL_PASSWORD first.");
            return;
        }

        try (
                Connection connection = MySqlConnectionFactory.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT VERSION(), DATABASE()")) {
            if (result.next()) {
                System.out.println(
                        "MySQL connection successful!");
                System.out.println(
                        "Server version: " + result.getString(1));
                System.out.println(
                        "Database: " + result.getString(2));
            }
        } catch (java.sql.SQLException e) {
            System.err.println("MySQL connection failed (SQLState="
                    + e.getSQLState() + ", vendorCode=" + e.getErrorCode()
                    + "): " + com.ruleweaver.config.MongoConfig.sanitize(
                            e.getMessage()));
        }
    }
}
