package org.example.equipmentlending.repository;

import java.sql.Connection;

public class JdbcItemRepository  implements ItemRepository{
    private final Connection connection;

    public JdbcItemRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection must not be null.");
        }
        this.connection = connection;
    }
}
