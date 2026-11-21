package org.example.equipmentlending.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.sql.SQLException;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.model.Student;
import org.example.equipmentlending.model.Item;
import org.example.equipmentlending.repository.JdbcChargeRepository;
import org.example.equipmentlending.repository.JdbcItemRepository;
import org.example.equipmentlending.repository.JdbcLoanRepository;
import org.example.equipmentlending.repository.JdbcStaffRepository;
import org.example.equipmentlending.repository.JdbcStudentRepository;

public class JdbcLoanRegistrationService {
    private final ConnectionFactory connectionFactory;
    public JdbcLoanRegistrationService(ConnectionFactory connectionFactory){
        if(connectionFactory == null){
            throw new IllegalArgumentException("Connection factory must not be null.");
        }
        this.connectionFactory = connectionFactory;
    }
    private void lockStudent(Connection connection, int studentId) throws SQLException{
        String sql = """
            SELECT id
            FROM students
            WHERE id = ?
            FOR UPDATE
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql) ) {
            statement.setInt(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Student not found: " + studentId);
                }
            }
        }
    }
    private void lockItem(Connection connection, int itemId) throws SQLException{
        String sql = """
            SELECT id
            FROM items
            WHERE id = ?
            FOR UPDATE
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql) ) {
            statement.setInt(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("item not found: " + itemId);
                }
            }
        }
    }
    public Loan registerLoan(int loanId, int studentId, int itemId, int registeredById, LocalDate borrowDate){
        if(loanId <= 0){
            throw new IllegalArgumentException("Loan id must be positive.");
        }
        if(studentId <= 0){
            throw new IllegalArgumentException("Student id must be positive.");
        }
        if(itemId <= 0){
            throw new IllegalArgumentException("Item id must be positive.");
        }
        if(registeredById <= 0){
            throw new IllegalArgumentException("Person registering must have a positive id.");
        }
        if(borrowDate == null){
            throw new IllegalArgumentException("Borrow date can not be null.");
        }
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            JdbcStudentRepository studentRepository = new JdbcStudentRepository(connection);
            JdbcStaffRepository staffRepository = new JdbcStaffRepository(connection);
            JdbcItemRepository itemRepository = new JdbcItemRepository(connection);
            JdbcLoanRepository loanRepository = new JdbcLoanRepository(connection);
            JdbcChargeRepository chargeRepository = new JdbcChargeRepository(connection);
            try{
                lockStudent(connection, studentId);
                lockItem(connection, itemId);
                Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Student not found: " + studentId
                    ));
                Staff registeredBy = staffRepository.findById(registeredById)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Staff not found: " + registeredById
                    ));

                Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Item not found: " + itemId
                    ));
                LoanRegistrationService loanRegistrationService = new LoanRegistrationService(loanRepository, chargeRepository, itemRepository);
                Loan loan = loanRegistrationService.registerLoan(loanId, student, item, registeredBy, borrowDate);
                connection.commit();
                return loan;
            }
            catch (SQLException | RuntimeException failure){
                try {
                    connection.rollback();
                } catch (SQLException  rollebackfailure) {
                    failure.addSuppressed(rollebackfailure);
                }
                throw failure;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not register loan " + loanId,exception);
        }
    }
}
