package org.example.equipmentlending;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.LocalDate;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.repository.*;

public class JdbcChargeRepositoryTest {
    private static final int STUDENT_ID = 2_000_000_020;
    private static final int STAFF_ID = 2_000_000_021;
    private static final int ITEM_ID = 2_000_000_022;
    private static final int LOAN_ID = 2_000_000_023;

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            int chargeId;
            try {
                chargeId = runTest(connection);
            } finally {
                connection.rollback();
            }

            ChargeRepository charges = new JdbcChargeRepository(connection);

            check(charges.findById(chargeId).isEmpty(),
                "Test charge still exists after rollback.");

            System.out.println("PASS: rollback removed the test charge.");
        }

        System.out.println("All charge repository tests passed.");
    }

    private static int runTest(Connection connection) throws SQLException {
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
        Loan loan = new Loan(LOAN_ID, student, item, staff, borrowDate);

        students.save(student);
        staffRepository.save(staff);
        items.save(item);
        loans.save(loan);

        check(charges.findByStudentId(STUDENT_ID).isEmpty(),
            "New test student should have no charges.");

        // 1. Insert a new charge and retrieve its generated ID.
        StudentCharge charge = new StudentCharge(
            loan,
            ChargeType.LATE_FEE,
            new BigDecimal("15.00"),
            "Repository test charge."
        );

        check(charge.getId() == null,
            "A new charge should not already have an ID.");

        charges.save(charge);
        int chargeId = generatedId(charge);
        StudentCharge loaded = loadCharge(charges, chargeId);

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

        System.out.println("PASS: generated charge ID and unpaid charge restored.");

        // 2. Save and restore a partial payment.
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

        loaded = loadCharge(charges, chargeId);

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
        check(!loaded.isPaid(), "Partially paid charge must remain unpaid.");

        System.out.println("PASS: partial payment and its ID were restored.");

        // 3. Repeated saves must not duplicate charges or payments.
        charges.save(charge);
        charges.save(loaded);
        loaded = loadCharge(charges, chargeId);

        check(charge.getId() == chargeId,
            "Saving again changed the charge ID.");
        check(charges.findByStudentId(STUDENT_ID).size() == 1,
            "Saving again duplicated the charge.");
        check(loaded.getPayments().size() == 1,
            "Saving again duplicated a payment.");
        check(loaded.getTotalPaid().compareTo(new BigDecimal("5.00")) == 0,
            "Total paid changed after repeated saving.");

        System.out.println("PASS: repeated saving duplicates neither charge nor payments.");

        // 4. Pay the remaining amount.
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

        StudentCharge fullyPaid = loadCharge(charges, chargeId);

        check(fullyPaid.getPayments().size() == 2, "Expected two payments.");
        check(fullyPaid.getTotalPaid().compareTo(new BigDecimal("15.00")) == 0,
            "Wrong total paid.");
        check(fullyPaid.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0,
            "Fully paid charge should have no remaining amount.");
        check(fullyPaid.isPaid(), "Charge should be fully paid.");

        var studentCharges = charges.findByStudentId(STUDENT_ID);

        check(studentCharges.size() == 1,
            "Expected one charge for the test student.");
        check(studentCharges.get(0).getId() == chargeId,
            "Wrong charge returned for student.");
        check(studentCharges.get(0).isPaid(),
            "Student lookup did not restore payment status.");

        System.out.println("PASS: full payment and student lookup.");

        // 5. Reject a charge ID whose insert was rolled back.
        Savepoint beforeCharge = connection.setSavepoint();
        try {
            StudentCharge rolledBackCharge = new StudentCharge(
                loan, ChargeType.DAMAGE, new BigDecimal("20.00"),
                "Charge rollback test."
            );

            charges.save(rolledBackCharge);
            int rolledBackId = generatedId(rolledBackCharge);

            connection.rollback(beforeCharge);

            check(charges.findById(rolledBackId).isEmpty(),
                "Charge still exists after rollback.");
            check(rolledBackCharge.getId() == rolledBackId,
                "Java charge should retain its assigned ID.");

            expectFailure(
                () -> charges.save(rolledBackCharge),
                "Charge not found: " + rolledBackId
            );

            check(charges.findById(rolledBackId).isEmpty(),
                "Saving a stale charge recreated its row.");

            System.out.println("PASS: charge ID left over after rollback is rejected.");
        } finally {
            connection.rollback(beforeCharge);
            connection.releaseSavepoint(beforeCharge);
        }

        // 6. Keep the charge, but roll back its new payment.
        Savepoint beforePaymentScenario = connection.setSavepoint();
        try {
            StudentCharge paymentCharge = new StudentCharge(
                loan, ChargeType.LATE_FEE, new BigDecimal("15.00"),
                "Payment rollback test."
            );

            charges.save(paymentCharge);
            int paymentChargeId = generatedId(paymentCharge);

            Savepoint beforePayment = connection.setSavepoint();
            try {
                Payment rollbackPayment = new Payment(
                    new BigDecimal("5.00"),
                    paymentDate,
                    "TEST-ROLLBACK-PAYMENT",
                    staff
                );

                paymentCharge.addPayment(rollbackPayment);
                charges.save(paymentCharge);

                Integer paymentId = rollbackPayment.getId();
                check(paymentId != null && paymentId > 0,
                    "Payment should have received an ID.");

                connection.rollback(beforePayment);

                check(loadCharge(charges, paymentChargeId).getPayments().isEmpty(),
                    "Payment still exists after rollback.");
                check(paymentId.equals(rollbackPayment.getId()),
                    "Java payment should retain its assigned ID.");

                expectFailure(
                    () -> charges.save(paymentCharge),
                    "The payment wasn't found."
                );

                System.out.println(
                    "PASS: payment ID left over after rollback is rejected."
                );
            } finally {
                connection.rollback(beforePayment);
                connection.releaseSavepoint(beforePayment);
            }
        } finally {
            connection.rollback(beforePaymentScenario);
            connection.releaseSavepoint(beforePaymentScenario);
        }

        // 7. Reject a payment belonging to a different charge.
        Savepoint beforeWrongCharge = connection.setSavepoint();
        StudentCharge otherCharge = new StudentCharge(
            loan, ChargeType.DAMAGE, new BigDecimal("100.00"),
            "Test payment assigned to the wrong charge."
        );

        try {
            // Save first so the ownership check tests an existing charge.
            charges.save(otherCharge);
            generatedId(otherCharge);

            Payment existingPayment =
                loadCharge(charges, chargeId).getPayments().get(0);

            otherCharge.addPayment(existingPayment);

            expectFailure(
                () -> charges.save(otherCharge),
                "Charge id and stored id dont match."
            );
        } finally {
            connection.rollback(beforeWrongCharge);
            connection.releaseSavepoint(beforeWrongCharge);
        }

        check(charges.findById(otherCharge.getId()).isEmpty(),
            "Second test charge still exists after rollback.");

        StudentCharge unchanged = loadCharge(charges, chargeId);
        check(unchanged.getPayments().size() == 2,
            "Original charge's payments changed.");
        check(unchanged.getTotalPaid().compareTo(new BigDecimal("15.00")) == 0,
            "Original charge's total paid changed.");
        check(unchanged.isPaid(), "Original charge should still be fully paid.");
        check(charges.findByStudentId(STUDENT_ID).size() == 1,
            "Temporary test charges were not rolled back.");

        System.out.println("PASS: payment belonging to another charge is rejected.");

        return chargeId;
    }

    private static int generatedId(StudentCharge charge) {
        check(charge.getId() != null && charge.getId() > 0,
            "Charge was not assigned a generated ID.");
        return charge.getId();
    }

    private static StudentCharge loadCharge(
            ChargeRepository charges, int chargeId) {
        StudentCharge charge = charges.findById(chargeId)
            .orElseThrow(() -> new AssertionError("Saved charge was not found."));

        check(charge.getId() != null && charge.getId() == chargeId,
            "Wrong charge ID.");
        return charge;
    }

    private static void expectFailure(Runnable action, String expectedMessage) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            if (!expectedMessage.equals(exception.getMessage())) {
                throw new AssertionError(
                    "Unexpected failure. Expected: " + expectedMessage,
                    exception
                );
            }
            return;
        }

        throw new AssertionError("Expected failure: " + expectedMessage);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}