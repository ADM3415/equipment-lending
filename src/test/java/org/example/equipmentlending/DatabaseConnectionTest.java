package org.example.equipmentlending;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.example.equipmentlending.database.ConnectionFactory;

public class DatabaseConnectionTest {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        try (
            Connection connection = factory.openConnection();
            Statement statement = connection.createStatement();

            ResultSet result = statement.executeQuery(
                "SELECT DATABASE(), CURRENT_USER()"
            )
        ) {
            if (result.next()) {
                System.out.println("Connected successfully.");
                System.out.println("Database: " + result.getString(1));
                System.out.println("Account: " + result.getString(2));
            }
        }
    }
}