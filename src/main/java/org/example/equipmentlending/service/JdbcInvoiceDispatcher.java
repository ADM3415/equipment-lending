package org.example.equipmentlending.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.Invoice;
import org.example.equipmentlending.notification.InvoiceSender;
import org.example.equipmentlending.repository.JdbcInvoiceRepository;


public class JdbcInvoiceDispatcher {
    private final ConnectionFactory connectionFactory;
    private final InvoiceSender invoiceSender;
    public JdbcInvoiceDispatcher(ConnectionFactory connectionFactory, InvoiceSender invoiceSender){
        if(connectionFactory == null){
            throw new IllegalArgumentException("Connection factory must not be null.");
        }
        if(invoiceSender == null){
            throw new IllegalArgumentException("Invoice sender must not be null.");
        }
        this.connectionFactory = connectionFactory;
        this.invoiceSender = invoiceSender;
    }
    private void lockInvoice(Connection connection, int invoiceId) throws SQLException {
        String sql = """
            SELECT id
            FROM invoices
            WHERE id = ?
            FOR UPDATE
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql) ) {
            statement.setInt(1, invoiceId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Invoice not found: " + invoiceId);
                }
            }
        }
    }
    public void sendInvoice(int invoiceId){
        if( invoiceId <= 0){
            throw new IllegalArgumentException("invoice id must be positive.");
        }
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            InvoiceSendException invoiceSendException = null;
            try{ 
                lockInvoice(connection, invoiceId);
                JdbcInvoiceRepository invoiceRepository = new JdbcInvoiceRepository(connection);
                Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));
                InvoiceService service = new InvoiceService(invoiceSender, invoiceRepository);
                try{
                    service.sendInvoice(invoice);
                }
                catch(InvoiceSendException exception){
                    invoiceSendException = exception;
                }
                connection.commit();
            }
            catch (SQLException | RuntimeException failure) {
                if (invoiceSendException != null) {
                    failure.addSuppressed(invoiceSendException);
                }
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                
                throw failure;
            }
            if(invoiceSendException != null){
                throw invoiceSendException;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not dispatch invoice " + invoiceId, exception);
        }
    }
}
