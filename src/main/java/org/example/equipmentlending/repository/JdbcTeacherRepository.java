package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.sql.Statement;

import org.example.equipmentlending.model.Teacher;


public class JdbcTeacherRepository implements TeacherRepository{
    private final Connection connection;

    public JdbcTeacherRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException(
                "Connection must not be null."
            );
        }

        this.connection = connection;
    }

    @Override
    public Optional<Teacher> findById(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                "teacher ID must be positive."
            );
        }

        String sql = """
            SELECT s.id, m.full_name
            FROM teachers s
            JOIN members m ON m.id = s.member_id
            WHERE s.id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                Teacher teacher = new Teacher(
                    result.getString("full_name"),
                    result.getInt("id")
                );

                return Optional.of(teacher);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not load teacher with ID " + id,
                exception
            );
        }
    }

    

    private Optional<Integer> findMemberId(int teacherId)
            throws SQLException {

        String sql = """
            SELECT member_id
            FROM teachers
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, teacherId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getInt("member_id"));
                }

                return Optional.empty();
            }
        }
    }

    private int insertMember(Teacher teacher) throws SQLException {
        String sql = """
            INSERT INTO members (full_name, member_type)
            VALUES (?, ?)
            """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, teacher.getName());
            statement.setString(2, teacher.getType().name());

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                    "Expected one member to be inserted."
                );
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                        "No member ID was generated."
                    );
                }

                return keys.getInt(1);
            }
        }
    }

    private void insertTeacher(Teacher teacher, int memberId) throws SQLException {

        String sql = """
            INSERT INTO teachers (id, member_id) VALUES (?, ?)
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, teacher.getId());
            statement.setInt(2, memberId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                    "Expected one teacher member to be inserted."
                );
            }
        }
    }
    private void updateMemberName(Teacher teacher, int memberId) throws SQLException {
        String memberSql = """
            UPDATE members
            SET full_name = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(memberSql)) {

            statement.setString(1, teacher.getName());
            statement.setInt(2, memberId);
            statement.executeUpdate();
        }
    }
    @Override
    public void save(Teacher teacher) {
        if(teacher == null){
            throw new IllegalArgumentException("teacher must not be null.");
        }
        try {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                    "teacher saving requires a caller-managed transaction."
                );
            }

            Optional<Integer> memberId = findMemberId(teacher.getId());

            if(memberId.isPresent()){
                updateMemberName(teacher, memberId.get());
            } 
            else{
                int newMemberId = insertMember(teacher);
                insertTeacher(teacher, newMemberId);
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not save teacher with ID " + teacher.getId(),
                exception
            );
        }
    }
}
