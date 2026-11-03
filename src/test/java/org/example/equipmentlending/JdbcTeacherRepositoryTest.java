package org.example.equipmentlending;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.Teacher;
import org.example.equipmentlending.repository.JdbcTeacherRepository;

public class JdbcTeacherRepositoryTest  {
    public static void main(String[] args) throws Exception {
        int id = 2_000_000_002;
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            JdbcTeacherRepository repository = new JdbcTeacherRepository(connection);

            try {
                check(repository.findById(id).isEmpty(),
                    "Test ID is already in use. Choose another ID.");

                Teacher original = new Teacher(
                    "Test Teacher", id
                );
                repository.save(original);
                checkStoredTeacher(repository, original);
                checkStoredMemberType(connection, id);
                System.out.println("PASS: inserted and loaded a new Teacher.");

                Teacher updated = new Teacher(
                    "Updated Teacher", id
                );
                repository.save(updated);
                checkStoredTeacher(repository, updated);
                System.out.println("PASS: updated the Teacher's name and preserved the Teacher ID.");
            } finally {
                connection.rollback();
            }

            check(repository.findById(id).isEmpty(),
                "Teacher still exists after rollback.");
            System.out.println("PASS: rollback removed the test Teacher.");
        }
    }

    private static void checkStoredTeacher(
            JdbcTeacherRepository repository, Teacher expected) {

        Teacher actual = repository.findById(expected.getId())
            .orElseThrow(() -> new AssertionError("Saved Teacher was not found."));

        check(actual.getId() == expected.getId(), "Wrong Teacher ID.");
        check(actual.getName().equals(expected.getName()), "Wrong full name.");
    }

    private static void checkStoredMemberType(Connection connection, int teacherId) throws SQLException {
        String sql = """
            SELECT m.member_type
            FROM teachers t
            JOIN members m ON m.id = t.member_id
            WHERE t.id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, teacherId);

            try (ResultSet result = statement.executeQuery()) {
                check(result.next(), "No linked member found for teacher " + teacherId);
                String actualType = result.getString("member_type");
                check("TEACHER".equals(actualType),
                    "Expected stored member type TEACHER, got " + actualType);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
