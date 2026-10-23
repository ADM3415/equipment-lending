package org.example.equipmentlending.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {
    private final String url;
    private final String username;
    private final String password;

    public ConnectionFactory() {
        this.url = System.getenv("DB_URL");
        this.username = System.getenv("DB_USERNAME");
        this.password = System.getenv("DB_PASSWORD");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("DB_URL is not configured.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("DB_USERNAME is not configured.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD is not configured.");
        }
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}