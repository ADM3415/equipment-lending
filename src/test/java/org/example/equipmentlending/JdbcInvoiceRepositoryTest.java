package org.example.equipmentlending;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.repository.*;

public class JdbcInvoiceRepositoryTest {
    private static final int STUDENT_ID = 2_000_000_030;
    private static final int STAFF_ID = 2_000_000_031;
    private static final int ITEM_ID = 2_000_000_032;
    private static final int LOAN_ID = 2_000_000_033;
    private static final int INVOICE_ID = 2_000_000_035;
    private static final int OTHER_INVOICE_ID = 2_000_000_036;

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                runTest(connection);
            } finally {
                connection.rollback();
            }

            InvoiceRepository invoices =
                new JdbcInvoiceRepository(connection);

            check(invoices.findById(INVOICE_ID).isEmpty(),
                "Test invoice still exists after rollback.");

            check(invoices.findById(OTHER_INVOICE_ID).isEmpty(),
                "Second test invoice still exists after rollback.");

            System.out.println("PASS: rollback removed the test invoices.");
        }

        System.out.println("All invoice repository tests passed.");
    }

    private static void runTest(Connection connection) throws SQLException {
        StudentRepository students = new JdbcStudentRepository(connection);
        StaffRepository staffRepository = new JdbcStaffRepository(connection);
        ItemRepository items = new JdbcItemRepository(connection);
        LoanRepository loans = new JdbcLoanRepository(connection);
        ChargeRepository charges = new JdbcChargeRepository(connection);
        InvoiceRepository invoices = new JdbcInvoiceRepository(connection);

        check(students.findById(STUDENT_ID).isEmpty(),
            "Test student ID is already in use.");
        check(staffRepository.findById(STAFF_ID).isEmpty(),
            "Test staff ID is already in use.");
        check(items.findById(ITEM_ID).isEmpty(),
            "Test item ID is already in use.");
        check(loans.findById(LOAN_ID).isEmpty(),
            "Test loan ID is already in use.");
        check(invoices.findById(INVOICE_ID).isEmpty(),
            "Test invoice ID is already in use.");
        check(invoices.findById(OTHER_INVOICE_ID).isEmpty(),
            "Second test invoice ID is already in use.");

        Student student = new Student(
            "Invoice Test Student",
            true,
            "invoice-repository-test@example.com",
            STUDENT_ID
        );

        Staff staff = new Staff("Invoice Test Staff", STAFF_ID);

        Item item = new Item(
            ItemType.LAPTOP,
            ITEM_ID,
            "Invoice Test Laptop",
            ItemCondition.GOOD,
            "Test Shelf",
            ItemStatus.ON_LOAN
        );

        LocalDate borrowDate = LocalDate.of(2026, 10, 1);
        LocalDate issuedDate = LocalDate.of(2026, 10, 12);

        Loan loan = new Loan(
            LOAN_ID, student, item, staff, borrowDate
        );

        StudentCharge charge = new StudentCharge(
            loan,
            ChargeType.LATE_FEE,
            new BigDecimal("15.00"),
            "Invoice repository test charge."
        );

        students.save(student);
        staffRepository.save(staff);
        items.save(item);
        loans.save(loan);
        charges.save(charge);
        check(charge.getId() != null && charge.getId() > 0,"Charge was not assigned a generated ID.");
        int chargeId = charge.getId();
        // 1. Insert an unsent invoice.
        Invoice invoice = new Invoice(INVOICE_ID, charge, issuedDate);
        invoices.save(invoice);

        Invoice loaded = loadInvoice(invoices);

        check(loaded.getId() == INVOICE_ID, "Wrong invoice ID.");
        check(loaded.getCharge().getId() == chargeId, "Wrong charge ID.");
        check(issuedDate.equals(loaded.getIssuedDate()), "Wrong issued date.");
        check(!loaded.isSent(), "New invoice should be unsent.");
        check(loaded.getLastSendAttemptAt() == null,
            "New invoice should have no send attempt.");

        System.out.println("PASS: saved and loaded an unsent invoice.");

        // 2. Persist an unsuccessful send attempt.
        // Nanoseconds are a multiple of 1,000: exact microsecond precision.
        LocalDateTime firstAttempt =
            LocalDateTime.of(2026, 10, 12, 10, 30, 15, 123_456_000);

        invoice.recordSendAttempt(firstAttempt);
        invoices.save(invoice);

        loaded = loadInvoice(invoices);

        check(!loaded.isSent(),
            "Recording an attempt should not mark the invoice as sent.");
        check(firstAttempt.equals(loaded.getLastSendAttemptAt()),
            "Send attempt timestamp was not restored exactly.");

        System.out.println("PASS: unsent attempt timestamp was saved.");

        // 3. Persist successful sending.
        LocalDateTime successfulAttempt = firstAttempt.plusMinutes(5);

        invoice.recordSendAttempt(successfulAttempt);
        invoice.markAsSent();
        invoices.save(invoice);

        loaded = loadInvoice(invoices);

        check(loaded.isSent(), "Invoice should be marked sent.");
        check(successfulAttempt.equals(loaded.getLastSendAttemptAt()),
            "Successful attempt timestamp was not restored.");

        System.out.println("PASS: successful sending state was updated.");

        // 4. Saving again must not insert another invoice.
        invoices.save(invoice);
        invoices.save(loaded);

        check(countInvoicesForCharge(connection, chargeId) == 1,
            "Repeated saving created duplicate invoices.");

        System.out.println("PASS: repeated saving preserves one invoice.");

        // 5. A different invoice ID cannot reuse the same charge.
        Savepoint beforeDuplicate = connection.setSavepoint();

        try {
            Invoice duplicate = new Invoice(
                OTHER_INVOICE_ID,
                charge,
                issuedDate.plusDays(1)
            );

            try {
                invoices.save(duplicate);
                throw new AssertionError(
                    "Expected a second invoice for the same charge to fail."
                );
            } catch (IllegalStateException exception) {
                if (!(exception.getCause() instanceof SQLException sqlException)
                        || sqlException.getErrorCode() != 1062) {
                    throw new AssertionError(
                        "Expected MySQL duplicate-key rejection.",
                        exception
                    );
                }
            }

            check(invoices.findById(OTHER_INVOICE_ID).isEmpty(),
                "Rejected invoice should not exist.");

            Invoice original = loadInvoice(invoices);

            check(original.isSent(),
                "Duplicate attempt changed the original sent status.");
            check(issuedDate.equals(original.getIssuedDate()),
                "Duplicate attempt changed the original issued date.");
            check(successfulAttempt.equals(original.getLastSendAttemptAt()),
                "Duplicate attempt changed the original timestamp.");
            check(countInvoicesForCharge(connection, chargeId) == 1,
                "Expected exactly one invoice for the charge.");

            System.out.println(
                "PASS: second invoice for the same charge is rejected."
            );
        } finally {
            connection.rollback(beforeDuplicate);
            connection.releaseSavepoint(beforeDuplicate);
        }
    }

    private static Invoice loadInvoice(InvoiceRepository invoices) {
        return invoices.findById(INVOICE_ID)
            .orElseThrow(() -> new AssertionError(
                "Saved invoice was not found."
            ));
    }

    private static int countInvoicesForCharge(
            Connection connection, int chargeId) throws SQLException {

        String sql = "SELECT COUNT(*) FROM invoices WHERE charge_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, chargeId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Invoice count returned no row.");
                }
                return result.getInt(1);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}