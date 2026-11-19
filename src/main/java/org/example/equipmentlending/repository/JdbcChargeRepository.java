package org.example.equipmentlending.repository;

import java.sql.Statement;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.equipmentlending.model.ChargeType;
import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.model.Payment;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.model.StudentCharge;

public class JdbcChargeRepository implements ChargeRepository{
    private final Connection connection;
    private final LoanRepository loanRepository;
    public JdbcChargeRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection must not be null.");
        }
        this.connection = connection;
        this.loanRepository = new JdbcLoanRepository(connection);
    }
    private StudentCharge readCharge(ResultSet result) throws SQLException{
        int chargeId = result.getInt("id");
        int loanId = result.getInt("loan_id");
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalStateException(
                "Charge " + chargeId + " references missing loan " + loanId
            ));

        ChargeType type = ChargeType.valueOf(result.getString("type"));
        BigDecimal amount = result.getBigDecimal("amount");
        String description = result.getString("description");
        boolean storedPaid = result.getBoolean("is_paid");

        StudentCharge charge = new StudentCharge(
            chargeId, loan, type, amount, description
        );

        StaffRepository staffRepository = new JdbcStaffRepository(connection);

        String sql = """
            SELECT id, amount, payment_date, receipt_reference, processed_by_id
            FROM payments
            WHERE charge_id = ?
            ORDER BY payment_date, id
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, chargeId);

            try (ResultSet payments = statement.executeQuery()) {
                while (payments.next()) {
                    int staffId = payments.getInt("processed_by_id");

                    Staff processedBy = staffRepository.findById(staffId)
                        .orElseThrow(() -> new IllegalStateException(
                            "Payment for charge " + chargeId
                                + " references missing staff " + staffId
                        ));

                    Payment payment = new Payment(
                        payments.getBigDecimal("amount"),
                        payments.getObject("payment_date", LocalDate.class),
                        payments.getString("receipt_reference"),
                        processedBy,
                        payments.getObject("id", Integer.class)
                    );

                    charge.addPayment(payment);
                }
            }
        }

        if (charge.isPaid() != storedPaid) {
            throw new IllegalStateException(
                "Payment status does not match recorded payments for charge "
                    + chargeId
            );
        }

        return charge;
    }
    @Override 
    public Optional<StudentCharge> findById(int chargeId){
        if (chargeId <=0){
            throw new IllegalArgumentException("Charge id must be positive.");
        }
        String sql = """
        SELECT id, loan_id, type, amount, description, is_paid
        FROM student_charges
        WHERE id = ?
        """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql)){

            statement.setInt(1, chargeId);
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    return Optional.empty();
                }
                StudentCharge charge = readCharge(result);
                return Optional.of(charge);
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not find Charge with ID " + chargeId, exception);
        }
    }
    @Override 
    public List<StudentCharge> findByStudentId(int studentId){
        if (studentId <=0){
            throw new IllegalArgumentException("Student id must be positive.");
        }
        List<StudentCharge> studentCharges = new ArrayList<>();
        String sql = """
            SELECT c.id, c.loan_id, c.type, c.amount, c.description, c.is_paid
            FROM student_charges c
            JOIN loans l ON c.loan_id = l.id
            WHERE l.student_id = ?
            ORDER BY c.id
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setInt(1, studentId);
            try (ResultSet result = statement.executeQuery()){
                while(result.next()){
                    StudentCharge charge = readCharge(result);
                    studentCharges.add(charge);
                }
                return studentCharges;
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not find Charge for student with ID " + studentId, exception);
        }
    }
    private void insertPayment(Payment payment, StudentCharge charge){
        if (payment == null) {
            throw new IllegalArgumentException("Payment must not be null.");
        }
        if (charge == null) {
            throw new IllegalArgumentException("Charge must not be null.");
        }
        if (payment.getId() != null) {
            throw new IllegalStateException("Payment already has an ID.");
        }
        String sql = """
            INSERT INTO payments (
                charge_id, amount, payment_date, receipt_reference, processed_by_id
            )
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
            statement.setInt(1, charge.getId());
            statement.setBigDecimal(2, payment.getAmount());
            statement.setObject(3, payment.getPaymentDate());
            statement.setString(4, payment.getReceiptReference());
            statement.setInt(5, payment.getProcessedBy().getId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Expected one payment to be inserted.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()){
                if (!keys.next()) {
                    throw new SQLException("No payment ID was generated.");
                }
                payment.assignId(keys.getInt(1));
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not insert payment for charge " + charge.getId(), exception);
        }
    }
    private void verifyStoredPayment(Payment payment, StudentCharge charge){
        if(payment == null){
            throw new IllegalArgumentException("Payment must not be null.");
        }
        if(charge == null){
            throw new IllegalArgumentException("Charge must not be null.");
        }
        String sql = """
            SELECT charge_id
            FROM payments
            WHERE id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            if(payment.getId() == null){
                throw new IllegalStateException("Payment id can not be null.");
            }
            statement.setInt(1, payment.getId());
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    throw new IllegalStateException("The payment wasn't found.");
                }
                
                int storedChargeId = result.getInt("charge_id");
                if(charge.getId() != storedChargeId ){
                    throw new IllegalStateException("Charge id and stored id dont match.");
                }
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not find Charge id for payment with ID " + payment.getId(), exception);
        }
    }
    @Override 
    public void save(StudentCharge charge){
        if(charge == null){
            throw new IllegalArgumentException("Charge must not be null.");
        }
        String sql = """
            INSERT INTO student_charges (
                id, loan_id, type, amount, description, is_paid
            )
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                loan_id = ?,
                type = ?,
                amount = ?,
                description = ?,
                is_paid = ?
            """;
        try(PreparedStatement statement = connection.prepareStatement(sql)){
            if (connection.getAutoCommit()){
                throw new IllegalStateException("Charge saving requires a caller-managed transaction.");
            }
            statement.setInt(1, charge.getId());
            statement.setInt(2, charge.getLoan().getId());
            statement.setString(3, charge.getChargeType().name());
            statement.setBigDecimal(4, charge.getAmount());
            statement.setString(5, charge.getDescription());
            statement.setBoolean(6, charge.isPaid());
            statement.setInt(7, charge.getLoan().getId());
            statement.setString(8, charge.getChargeType().name());
            statement.setBigDecimal(9, charge.getAmount());
            statement.setString(10, charge.getDescription());
            statement.setBoolean(11, charge.isPaid());
            statement.executeUpdate();
            for(Payment payment : charge.getPayments()){
                if (payment.getId() == null) {
                    insertPayment(payment, charge);
                }
                else{
                    verifyStoredPayment(payment, charge);
                }
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not save charge with ID " + charge.getId(),exception);
        }
    }
}
