package com.inventory.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {
    private static final Path CONFIG_FILE = Path.of("config", "db.properties");

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (NoSuchFileException exception) {
            throw new SQLException(
                    "Database configuration file not found: " + CONFIG_FILE
                            + ". Copy config/db.properties.example to config/db.properties "
                            + "and enter your local database settings.",
                    exception);
        } catch (IOException exception) {
            throw new SQLException(
                    "Could not read database configuration file: " + CONFIG_FILE,
                    exception);
        }

        String url = getRequiredProperty(properties, "db.url");
        String username = getRequiredProperty(properties, "db.username");
        String password = getRequiredProperty(properties, "db.password");

        return DriverManager.getConnection(url, username, password);
    }

    private static String getRequiredProperty(Properties properties, String name)
            throws SQLException {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new SQLException(
                    "Missing or blank required property '" + name
                            + "' in " + CONFIG_FILE + ".");
        }
        return value;
    }
}
