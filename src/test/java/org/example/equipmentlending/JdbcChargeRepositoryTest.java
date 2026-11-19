package org.example.equipmentlending;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.sql.Savepoint;
import java.sql.SQLException;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.repository.*;


public class JdbcChargeRepositoryTest {
    private static final int STUDENT_ID = 2_000_000_020;
    private static final int STAFF_ID = 2_000_000_021;
    private static final int ITEM_ID = 2_000_000_022;
    private static final int LOAN_ID = 2_000_000_023;
    private static final int CHARGE_ID = 2_000_000_024;

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                runTest(connection);
            } finally {
                connection.rollback();
            }

            ChargeRepository charges = new JdbcChargeRepository(connection);

            check(charges.findById(CHARGE_ID).isEmpty(),
                "Test charge still exists after rollback.");

            System.out.println("PASS: rollback removed the test charge.");
        }

        System.out.println("All charge repository tests passed.");
    }

    private static void runTest(Connection connection) throws SQLException {
        StudentRepository students = new JdbcStudentRepository(connection);
        StaffRepository staffRepository = new JdbcStaffRepository(connection);
        ItemRepository items = new JdbcItemRepository(connection);
        LoanRepository loans = new JdbcLoanRepository(connection);
        ChargeRepository charges = new JdbcChargeRepository(connection);

        check(students.findById(STUDENT_ID).isEmpty(),
            "Test student ID is already in use.");
        check(staffRepository.findById(STAFF_ID).isEmpty(),
            "Test staff ID is already in use.");
        check(items.findById(ITEM_ID).isEmpty(),
            "Test item ID is already in use.");
        check(loans.findById(LOAN_ID).isEmpty(),
            "Test loan ID is already in use.");
        check(charges.findById(CHARGE_ID).isEmpty(),
            "Test charge ID is already in use.");

        Student student = new Student(
            "Charge Test Student",
            true,
            "charge-repository-test@example.com",
            STUDENT_ID
        );

        Staff staff = new Staff("Charge Test Staff", STAFF_ID);

        Item item = new Item(
            ItemType.LAPTOP,
            ITEM_ID,
            "Charge Test Laptop",
            ItemCondition.GOOD,
            "Test Shelf",
            ItemStatus.ON_LOAN
        );

        LocalDate borrowDate = LocalDate.of(2026, 10, 1);
        LocalDate paymentDate = borrowDate.plusDays(10);

        Loan loan = new Loan(
            LOAN_ID, student, item, staff, borrowDate
        );

        students.save(student);
        staffRepository.save(staff);
        items.save(item);
        loans.save(loan);

        check(charges.findByStudentId(STUDENT_ID).isEmpty(),
            "New test student should have no charges.");

        // 1. Save a charge without payments.
        StudentCharge charge = new StudentCharge(
            CHARGE_ID,
            loan,
            ChargeType.LATE_FEE,
            new BigDecimal("15.00"),
            "Repository test charge."
        );

        charges.save(charge);

        StudentCharge loaded = loadCharge(charges);

        check(loaded.getLoan().getId() == LOAN_ID, "Wrong loan.");
        check(loaded.getChargeType() == ChargeType.LATE_FEE,
            "Wrong charge type.");
        check(loaded.getAmount().compareTo(new BigDecimal("15.00")) == 0,
            "Wrong charge amount.");
        check("Repository test charge.".equals(loaded.getDescription()),
            "Wrong description.");
        check(loaded.getPayments().isEmpty(),
            "New charge should have no payments.");
        check(loaded.getTotalPaid().compareTo(BigDecimal.ZERO) == 0,
            "New charge should have zero paid.");
        check(!loaded.isPaid(), "New charge should be unpaid.");

        System.out.println("PASS: saved and loaded an unpaid charge.");

        // 2. Save a partial payment.
        Payment firstPayment = new Payment(
            new BigDecimal("5.00"),
            paymentDate,
            "TEST-CHARGE-PAYMENT-1",
            staff
        );

        charge.addPayment(firstPayment);
        charges.save(charge);

        check(firstPayment.getId() != null && firstPayment.getId() > 0,
            "Payment was not assigned a generated ID.");

        loaded = loadCharge(charges);

        check(loaded.getPayments().size() == 1,
            "Expected one partial payment.");

        Payment restoredPayment = loaded.getPayments().get(0);

        check(firstPayment.getId().equals(restoredPayment.getId()),
            "Payment ID was not restored.");
        check(restoredPayment.getAmount()
                .compareTo(new BigDecimal("5.00")) == 0,
            "Wrong payment amount.");
        check(paymentDate.equals(restoredPayment.getPaymentDate()),
            "Wrong payment date.");
        check("TEST-CHARGE-PAYMENT-1"
                .equals(restoredPayment.getReceiptReference()),
            "Wrong receipt reference.");
        check(restoredPayment.getProcessedBy().getId() == STAFF_ID,
            "Wrong processing staff.");
        check(loaded.getRemainingAmount()
                .compareTo(new BigDecimal("10.00")) == 0,
            "Wrong remaining amount.");
        check(!loaded.isPaid(),
            "A partially paid charge must remain unpaid.");

        System.out.println("PASS: partial payment and its ID were restored.");

        // 3. Saving again must not duplicate the payment.
        charges.save(charge);
        charges.save(loaded);

        loaded = loadCharge(charges);

        check(loaded.getPayments().size() == 1,
            "Saving again duplicated a payment.");
        check(loaded.getTotalPaid().compareTo(new BigDecimal("5.00")) == 0,
            "Total paid changed after repeated saving.");

        System.out.println("PASS: repeated saving does not duplicate payments.");

        // 4. Pay the remaining amount using the reloaded charge.
        Payment finalPayment = new Payment(
            new BigDecimal("10.00"),
            paymentDate.plusDays(1),
            "TEST-CHARGE-PAYMENT-2",
            staff
        );

        loaded.addPayment(finalPayment);
        charges.save(loaded);

        check(finalPayment.getId() != null && finalPayment.getId() > 0,
            "Final payment was not assigned an ID.");
        check(!finalPayment.getId().equals(firstPayment.getId()),
            "Different payments should have different IDs.");

        StudentCharge fullyPaid = loadCharge(charges);

        check(fullyPaid.getPayments().size() == 2,
            "Expected two payments.");
        check(fullyPaid.getTotalPaid()
                .compareTo(new BigDecimal("15.00")) == 0,
            "Wrong total paid.");
        check(fullyPaid.getRemainingAmount()
                .compareTo(BigDecimal.ZERO) == 0,
            "Fully paid charge should have no remaining amount.");
        check(fullyPaid.isPaid(),
            "Charge should be fully paid.");

        var studentCharges = charges.findByStudentId(STUDENT_ID);

        check(studentCharges.size() == 1,
            "Expected one charge for the test student.");
        check(studentCharges.get(0).getId() == CHARGE_ID,
            "Wrong charge returned for student.");
        check(studentCharges.get(0).isPaid(),
            "Student lookup did not restore payment status.");

        System.out.println("PASS: full payment and student lookup.");
        int rollbackChargeId = 2_000_000_025;
        check(charges.findById(rollbackChargeId).isEmpty(),
            "Rollback test charge ID is already in use.");

        Savepoint beforePayment = connection.setSavepoint();

        try {
            StudentCharge rollbackCharge = new StudentCharge(
                rollbackChargeId,
                loan,
                ChargeType.LATE_FEE,
                new BigDecimal("15.00"),
                "Payment rollback test."
            );

            Payment rollbackPayment = new Payment(
                new BigDecimal("5.00"),
                paymentDate,
                "TEST-ROLLBACK-PAYMENT",
                staff
            );

            rollbackCharge.addPayment(rollbackPayment);
            charges.save(rollbackCharge);

            Integer assignedId = rollbackPayment.getId();

            check(assignedId != null,
                "Payment should have received an ID.");

            // Undo the database inserts, keeping the Java objects.
            connection.rollback(beforePayment);

            check(charges.findById(rollbackChargeId).isEmpty(),
                "Charge still exists after rollback.");

            check(assignedId.equals(rollbackPayment.getId()),
                "Java payment should still hold its assigned ID.");

            // Saving again must detect that the payment row is missing.
            try {
                charges.save(rollbackCharge);

                throw new AssertionError(
                    "Expected saving to reject the missing payment."
                );
            } catch (IllegalStateException exception) {
                if (!"The payment wasn't found.".equals(exception.getMessage())) {
                    throw new AssertionError(
                        "Unexpected failure instead of missing-payment rejection.",
                        exception
                    );
                }
            }

            System.out.println(
                "PASS: saving rejects a payment ID left over after rollback."
            );
        } finally {
            // The failed retry may have reinserted the charge row.
            connection.rollback(beforePayment);
            connection.releaseSavepoint(beforePayment);
        }
        int otherChargeId = 2_000_000_026;
        check(charges.findById(otherChargeId).isEmpty(),
            "Second test charge ID is already in use.");

        Savepoint beforeWrongCharge = connection.setSavepoint();

        try {
            StudentCharge originalCharge = loadCharge(charges);
            Payment existingPayment = originalCharge.getPayments().get(0);

            StudentCharge otherCharge = new StudentCharge(
                otherChargeId,
                loan,
                ChargeType.DAMAGE,
                new BigDecimal("100.00"),
                "Test payment assigned to the wrong charge."
            );

            // This payment already belongs to the original charge.
            otherCharge.addPayment(existingPayment);

            try {
                charges.save(otherCharge);

                throw new AssertionError(
                    "Expected saving to reject a payment belonging to another charge."
                );
            } catch (IllegalStateException exception) {
                if (!"Charge id and stored id dont match."
                        .equals(exception.getMessage())) {
                    throw new AssertionError(
                        "Unexpected failure instead of payment ownership rejection.",
                        exception
                    );
                }
            }
        } finally {
            connection.rollback(beforeWrongCharge);
            connection.releaseSavepoint(beforeWrongCharge);
        }

        check(charges.findById(otherChargeId).isEmpty(),
            "Second test charge still exists after rollback.");

        StudentCharge unchangedCharge = loadCharge(charges);

        check(unchangedCharge.getPayments().size() == 2,
            "Original charge's payments were changed.");

        check(unchangedCharge.getTotalPaid()
                .compareTo(new BigDecimal("15.00")) == 0,
            "Original charge's total paid was changed.");

        check(unchangedCharge.isPaid(),
            "Original charge should still be fully paid.");

        System.out.println(
            "PASS: payment belonging to another charge is rejected."
        );
    }

    private static StudentCharge loadCharge(ChargeRepository charges) {
        return charges.findById(CHARGE_ID)
            .orElseThrow(() -> new AssertionError(
                "Saved charge was not found."
            ));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}