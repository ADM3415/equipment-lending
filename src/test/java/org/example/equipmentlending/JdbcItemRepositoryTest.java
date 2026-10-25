package org.example.equipmentlending;

import java.sql.Connection;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.repository.JdbcItemRepository;

public class JdbcItemRepositoryTest {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                JdbcItemRepository repository =
                    new JdbcItemRepository(connection);

                int testId = 2_000_000_001;

                if (repository.findById(testId).isPresent()) {
                    throw new IllegalStateException(
                        "Test ID already exists. Choose another unused ID."
                    );
                }

                Item item = new Item(
                    ItemType.LAPTOP,
                    testId,
                    "Test Laptop",
                    ItemCondition.GOOD,
                    "Test Shelf",
                    ItemStatus.IN_STOCK
                );

                repository.save(item);

                Item loaded = repository.findById(testId).orElseThrow();

                check(
                    loaded.getId() == testId
                        && loaded.getName().equals("Test Laptop")
                        && loaded.getType() == ItemType.LAPTOP
                        && loaded.getCondition() == ItemCondition.GOOD
                        && loaded.getStorageLocation().equals("Test Shelf")
                        && loaded.getStatus() == ItemStatus.IN_STOCK,
                    "Loaded item does not match the saved item."
                );

                System.out.println("PASS: insert and load.");

                loaded.markAsLoaned();
                repository.save(loaded);

                Item updated = repository.findById(testId).orElseThrow();

                check(
                    updated.getStatus() == ItemStatus.ON_LOAN,
                    "Updated status was not saved."
                );

                System.out.println("PASS: update and reload.");
            } finally {
                connection.rollback();
            }

            System.out.println("Test changes rolled back.");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}