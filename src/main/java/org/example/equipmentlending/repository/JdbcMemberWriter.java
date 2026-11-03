package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.example.equipmentlending.model.Member;

final class JdbcMemberWriter {
    private final Connection connection;

    JdbcMemberWriter(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection must not be null.");
        }
        this.connection = connection;
    }

    int insertMember(Member member) throws SQLException {
        String sql = """
            INSERT INTO members (full_name, member_type)
            VALUES (?, ?)
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, member.getName());
            statement.setString(2, member.getType().name());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Expected one member to be inserted.");
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No member ID was generated.");
                }
                return keys.getInt(1);
            }
        }
    }

    void updateMemberName(int memberId, String fullName) throws SQLException {
        String sql = """
            UPDATE members
            SET full_name = ?
            WHERE id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fullName);
            statement.setInt(2, memberId);
            statement.executeUpdate();
        }
    }
}