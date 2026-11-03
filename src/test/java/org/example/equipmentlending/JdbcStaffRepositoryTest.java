package org.example.equipmentlending;

import java.sql.Connection;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.repository.JdbcStaffRepository;

public class JdbcStaffRepositoryTest  {
    public static void main(String[] args) throws Exception {
        int id = 2_000_000_002;
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            JdbcStaffRepository repository = new JdbcStaffRepository(connection);

            try {
                check(repository.findById(id).isEmpty(),
                    "Test ID is already in use. Choose another ID.");

                Staff original = new Staff(
                    "Test Staff", id
                );
                repository.save(original);
                checkStoredStaff(repository, original);
                System.out.println("PASS: inserted and loaded a new Staff.");

                Staff updated = new Staff(
                    "Updated Staff", id
                );
                repository.save(updated);
                checkStoredStaff(repository, updated);
                System.out.println("PASS: updated the staff member's name and preserved the staff ID.");
            } finally {
                connection.rollback();
            }

            check(repository.findById(id).isEmpty(),
                "Staff still exists after rollback.");
            System.out.println("PASS: rollback removed the test Staff.");
        }
    }

    private static void checkStoredStaff(
            JdbcStaffRepository repository, Staff expected) {

        Staff actual = repository.findById(expected.getId())
            .orElseThrow(() -> new AssertionError("Saved Staff was not found."));

        check(actual.getId() == expected.getId(), "Wrong Staff ID.");
        check(actual.getName().equals(expected.getName()), "Wrong full name.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
