package org.example.equipmentlending;

import java.sql.Connection;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.Student;
import org.example.equipmentlending.repository.JdbcStudentRepository;

public class JdbcStudentRepositoryTest {

    public static void main(String[] args) throws Exception {
        int id = 2_000_000_002;
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            JdbcStudentRepository repository = new JdbcStudentRepository(connection);

            try {
                check(repository.findById(id).isEmpty(),
                    "Test ID is already in use. Choose another ID.");

                Student original = new Student(
                    "Test Student", true, "student-original@example.com", id
                );
                repository.save(original);
                checkStoredStudent(repository, original);
                System.out.println("PASS: inserted and loaded a new student.");

                Student updated = new Student(
                    "Updated Student", false, "student-updated@example.com", id
                );
                repository.save(updated);
                checkStoredStudent(repository, updated);
                System.out.println("PASS: updated the existing student's name, email and status.");
            } finally {
                connection.rollback();
            }

            check(repository.findById(id).isEmpty(),
                "Student still exists after rollback.");
            System.out.println("PASS: rollback removed the test student.");
        }
    }

    private static void checkStoredStudent(
            JdbcStudentRepository repository, Student expected) {

        Student actual = repository.findById(expected.getId())
            .orElseThrow(() -> new AssertionError("Saved student was not found."));

        check(actual.getId() == expected.getId(), "Wrong student ID.");
        check(actual.getName().equals(expected.getName()), "Wrong full name.");
        check(actual.isActive() == expected.isActive(), "Wrong active status.");
        check(actual.getUniversityEmail().equals(expected.getUniversityEmail()),
            "Wrong university email.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}