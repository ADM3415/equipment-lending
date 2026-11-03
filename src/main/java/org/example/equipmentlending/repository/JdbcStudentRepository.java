package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.example.equipmentlending.model.Student;

public class JdbcStudentRepository implements StudentRepository {
    private final Connection connection;
    private final JdbcMemberWriter memberWriter;

    public JdbcStudentRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException(
                "Connection must not be null."
            );
        }

        this.connection = connection;
        this.memberWriter = new JdbcMemberWriter(connection);
    }

    @Override
    public Optional<Student> findById(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                "Student ID must be positive."
            );
        }

        String sql = """
            SELECT s.id, s.active, s.university_email, m.full_name
            FROM students s
            JOIN members m ON s.member_id = m.id
            WHERE s.id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                Student student = new Student(
                    result.getString("full_name"),
                    result.getBoolean("active"),
                    result.getString("university_email"),
                    result.getInt("id")
                );

                return Optional.of(student);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not load student with ID " + id,
                exception
            );
        }
    }

    

    private Optional<Integer> findMemberId(int studentId)
            throws SQLException {

        String sql = """
            SELECT member_id
            FROM students
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getInt("member_id"));
                }

                return Optional.empty();
            }
        }
    }

    private void insertStudent(Student student, int memberId)
            throws SQLException {

        String sql = """
            INSERT INTO students
                (id, active, university_email, member_id)
            VALUES (?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, student.getId());
            statement.setBoolean(2, student.isActive());
            statement.setString(3, student.getUniversityEmail());
            statement.setInt(4, memberId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                    "Expected one student to be inserted."
                );
            }
        }
    }
    private void updateStudent(Student student, int memberId) throws SQLException {
        memberWriter.updateMemberName(memberId, student.getName());
        String studentSql = """
            UPDATE students
            SET active = ?, university_email = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(studentSql)) {

            statement.setBoolean(1, student.isActive());
            statement.setString(2, student.getUniversityEmail());
            statement.setInt(3, student.getId());
            statement.executeUpdate();
        }
    }
    @Override
    public void save(Student student) {
        if (student == null) {
            throw new IllegalArgumentException(
                "Student must not be null."
            );
        }

        try {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                    "Student saving requires a caller-managed transaction."
                );
            }

            Optional<Integer> memberId = findMemberId(student.getId());

            if(memberId.isPresent()){
                updateStudent(student, memberId.get());
            } 
            else{
                int newMemberId = memberWriter.insertMember(student);
                insertStudent(student, newMemberId);
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not save student with ID " + student.getId(),
                exception
            );
        }
    }
}