package org.example.equipmentlending.service;

import java.time.LocalDate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.equipmentlending.model.MemberType;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.model.Student;
import org.example.equipmentlending.repository.JdbcChargeRepository;
import org.example.equipmentlending.repository.JdbcItemRepository;
import org.example.equipmentlending.repository.JdbcLoanRepository;
import org.example.equipmentlending.repository.JdbcStaffRepository;
import org.example.equipmentlending.repository.JdbcStudentRepository;
import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.ItemCondition;


public class JdbcLoanReturnService {
    private final ConnectionFactory connectionFactory;

    public JdbcLoanReturnService(ConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("Connection factory must not be null.");
        }
        this.connectionFactory = connectionFactory;
    }
    public Loan returnLoan(int loanId, MemberType returnedByType, int returnedById, int processedById, LocalDate returnDate, ItemCondition returnCondition, String description){
        if(loanId <= 0){
            throw new IllegalArgumentException("Loan id must be positive.");
        }
        if(returnedByType == null){
            throw new IllegalArgumentException("Must provide the member's type.");
        }
        if(returnedById <= 0){
            throw new IllegalArgumentException("Reterner's  id must be positive");
        }
        if(processedById <= 0){
            throw new IllegalArgumentException("Processor's id must be positive");
        }
        if(returnDate == null){
            throw new IllegalArgumentException("Must provide the return date.");
        }
        if(returnCondition == null){
            throw new IllegalArgumentException("Must provide return condition.");
        }
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            JdbcStudentRepository studentRepository = new JdbcStudentRepository(connection);
            JdbcStaffRepository staffRepository = new JdbcStaffRepository(connection);
            JdbcItemRepository itemRepository = new JdbcItemRepository(connection);
            JdbcLoanRepository loanRepository = new JdbcLoanRepository(connection);
            JdbcChargeRepository chargeRepository = new JdbcChargeRepository(connection);
            try{
                String sql = """
                    SELECT student_id, item_id
                    FROM loans
                    WHERE id = ?
                """;
                int studentid;
                int itemId;
                try (PreparedStatement statement = connection.prepareStatement(sql) ) {
                    statement.setInt(1, loanId);
                    
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Loan not found: " + loanId);
                        }
                        studentid = result.getInt("student_id");
                        itemId = result.getInt("item_id");
                    }
                }
                throw new UnsupportedOperationException("Return logic not implemented yet.");
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
            throw new IllegalStateException("Could not return loan " + loanId,exception);
        }
    }
}
