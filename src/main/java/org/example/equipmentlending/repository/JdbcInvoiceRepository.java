package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.example.equipmentlending.model.Invoice;
import org.example.equipmentlending.model.StudentCharge;

public class JdbcInvoiceRepository implements InvoiceRepository {
    private final Connection connection;
    private final ChargeRepository chargeRepository;
    public JdbcInvoiceRepository(Connection connection){
        if (connection == null) {
            throw new IllegalArgumentException("Connection must not be null.");
        }
        this.connection = connection;
        this.chargeRepository = new JdbcChargeRepository(connection);
    }
    private Invoice readInvoice(ResultSet result) throws SQLException{
        int invoiceId = result.getInt("id");
        int chargeId = result.getInt("charge_id");
        StudentCharge charge = chargeRepository.findById(chargeId).orElseThrow(() -> new IllegalStateException("invoice " + invoiceId + " references missing charge " + chargeId));
        LocalDate issuedDate = result.getObject("issued_date",LocalDate.class);
        boolean isSent = result.getBoolean("is_sent");
        LocalDateTime lastSendAttemptAt = result.getObject("last_send_attempt_at",LocalDateTime.class);
        Invoice invoice = new Invoice(invoiceId, charge, issuedDate, isSent, lastSendAttemptAt);
        return invoice;
    }
    @Override 
    public Optional<Invoice> findById(int id){
        if (id <=0){
            throw new IllegalArgumentException("invoice id must be positive.");
        }
        String sql = """
            SELECT id, charge_id, issued_date, is_sent, last_send_attempt_at
            FROM invoices
            WHERE id = ?
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql)){

            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    return Optional.empty();
                }
                Invoice invoice = readInvoice(result);
                return Optional.of(invoice);
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not find invoice with ID " + id, exception);
        }
    }
    @Override 
    public void save(Invoice invoice){
        if(invoice == null){
            throw new IllegalArgumentException("Invoice must not be null.");
        }
        
        try {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("Invoice saving requires a caller-managed transaction.");
            }
            boolean exists = findById(invoice.getId()).isPresent();
            String sql = exists ? """
                    UPDATE invoices
                    SET charge_id = ?,
                        issued_date = ?,
                        is_sent = ?,
                        last_send_attempt_at = ?
                    WHERE id = ?
                    """
                : """
                    INSERT INTO invoices (
                        id, charge_id, issued_date,
                        is_sent, last_send_attempt_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try(PreparedStatement statement = connection.prepareStatement(sql)){
                int index = 1;
                if (!exists){
                    statement.setInt(index++, invoice.getId());
                }
                statement.setInt(index++, invoice.getCharge().getId());
                statement.setObject(index++, invoice.getIssuedDate());
                statement.setBoolean(index++, invoice.isSent());
                if(invoice.getLastSendAttemptAt() == null){
                    statement.setNull(index++, Types.TIMESTAMP);
                } 
                else{
                    statement.setObject(index++, invoice.getLastSendAttemptAt());
                }
                if(exists){
                    statement.setInt(index, invoice.getId());
                }
                statement.executeUpdate();
            }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not save invoice with ID " + invoice.getId(),exception);
        }
    }
}
