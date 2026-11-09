package com.inventory;

import com.inventory.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnectionTest {
    private DatabaseConnectionTest() {
    }

    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION(), DATABASE()")) {
            System.out.println("JDBC connection established.");

            if (resultSet.next()) {
                System.out.println("MySQL version: " + resultSet.getString(1));
                System.out.println("Database: " + resultSet.getString(2));
            } else {
                throw new SQLException("The version and database query returned no result.");
            }
        } catch (SQLException exception) {
            System.err.println(
                    "JDBC connection test failed. Check config/db.properties and MySQL availability.");
            System.err.printf(
                    "Exception: %s: %s%n",
                    exception.getClass().getName(),
                    exception.getMessage());
            System.err.printf(
                    "SQLState: %s; error code: %d%n",
                    exception.getSQLState(),
                    exception.getErrorCode());

            Throwable cause = exception;
            while (cause != null) {
                cause = cause.getCause();
                if (cause != null) {
                    System.err.printf(
                            "Cause: %s: %s%n",
                            cause.getClass().getName(),
                            cause.getMessage());
                }
            }

            SQLException nextException = exception.getNextException();
            while (nextException != null) {
                System.err.printf(
                        "Next SQLException: %s: %s%n",
                        nextException.getClass().getName(),
                        nextException.getMessage());
                System.err.printf(
                        "SQLState: %s; error code: %d%n",
                        nextException.getSQLState(),
                        nextException.getErrorCode());
                nextException = nextException.getNextException();
            }
            System.exit(1);
        }
    }
}
