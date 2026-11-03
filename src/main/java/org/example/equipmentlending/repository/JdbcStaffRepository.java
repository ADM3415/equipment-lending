package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.sql.Statement;

import org.example.equipmentlending.model.Staff;


public class JdbcStaffRepository implements StaffRepository{
    private final Connection connection;

    public JdbcStaffRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException(
                "Connection must not be null."
            );
        }

        this.connection = connection;
    }

    @Override
    public Optional<Staff> findById(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                "Staff ID must be positive."
            );
        }

        String sql = """
            SELECT s.id, m.full_name
            FROM staff s
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

                Staff staff = new Staff(
                    result.getString("full_name"),
                    result.getInt("id")
                );

                return Optional.of(staff);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not load staff with ID " + id,
                exception
            );
        }
    }

    

    private Optional<Integer> findMemberId(int staffId)
            throws SQLException {

        String sql = """
            SELECT member_id
            FROM staff
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, staffId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getInt("member_id"));
                }

                return Optional.empty();
            }
        }
    }

    private int insertMember(Staff staff) throws SQLException {
        String sql = """
            INSERT INTO members (full_name, member_type)
            VALUES (?, ?)
            """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, staff.getName());
            statement.setString(2, staff.getType().name());

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

    private void insertStaff(Staff staff, int memberId) throws SQLException {

        String sql = """
            INSERT INTO staff (id, member_id) VALUES (?, ?)
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, staff.getId());
            statement.setInt(2, memberId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                    "Expected one staff member to be inserted."
                );
            }
        }
    }
    private void updateMemberName(Staff staff, int memberId) throws SQLException {
        String memberSql = """
            UPDATE members
            SET full_name = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(memberSql)) {

            statement.setString(1, staff.getName());
            statement.setInt(2, memberId);
            statement.executeUpdate();
        }
    }
    @Override
    public void save(Staff staff) {
        if(staff == null){
            throw new IllegalArgumentException("Staff must not be null.");
        }
        try {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                    "Staff saving requires a caller-managed transaction."
                );
            }

            Optional<Integer> memberId = findMemberId(staff.getId());

            if(memberId.isPresent()){
                updateMemberName(staff, memberId.get());
            } 
            else{
                int newMemberId = insertMember(staff);
                insertStaff(staff, newMemberId);
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not save staff with ID " + staff.getId(),
                exception
            );
        }
    }
}
