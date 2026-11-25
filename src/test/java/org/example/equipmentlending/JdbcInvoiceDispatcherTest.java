package org.example.equipmentlending;

import java.math.BigDecimal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.LocalDate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.notification.InvoiceSender;
import org.example.equipmentlending.repository.*;
import org.example.equipmentlending.service.InvoiceSendException;
import org.example.equipmentlending.service.JdbcInvoiceDispatcher;

public class JdbcInvoiceDispatcherTest {
    private static final int STUDENT_ID = 2_000_000_100;
    private static final int STAFF_ID = 2_000_000_101;
    private static final int ITEM_ID = 2_000_000_102;
    private static final int LOAN_ID = 2_000_000_103;
    private static final int INVOICE_ID = 2_000_000_105;
    private static final int MISSING_INVOICE_ID = 2_000_000_106;

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        testConcurrentSending(factory);
        int chargeId = createFixtures(factory);
        // If setup fails, it rolls back and no cleanup is attempted.
        try {
            AtomicInteger sendCount = new AtomicInteger();

            InvoiceSender successfulSender = invoice -> {
                sendCount.incrementAndGet();
            };

            RuntimeException smtpFailure =
                new RuntimeException("Simulated SMTP failure.");

            InvoiceSender failingSender = invoice -> {
                sendCount.incrementAndGet();
                throw smtpFailure;
            };

            JdbcInvoiceDispatcher failingDispatcher =
                new JdbcInvoiceDispatcher(factory, failingSender);

            // 1. A failed first attempt must still be committed.
            expectSendFailure(failingDispatcher, smtpFailure);

            Invoice failed = loadInvoice(factory);

            check(!failed.isSent(),
                "Failed first attempt must leave isSent false.");
            check(failed.getLastSendAttemptAt() != null,
                "Failed attempt timestamp was not committed.");

            System.out.println(
                "PASS: failed first attempt was committed."
            );

            // 2. Successful sending must commit the sent status.
            JdbcInvoiceDispatcher successfulDispatcher =
                new JdbcInvoiceDispatcher(factory, successfulSender);

            successfulDispatcher.sendInvoice(INVOICE_ID);

            Invoice sent = loadInvoice(factory);

            check(sent.isSent(),
                "Successful sending was not committed.");
            check(sent.getLastSendAttemptAt() != null,
                "Successful attempt timestamp was not committed.");

            System.out.println(
                "PASS: successful sending was committed."
            );

            // 3. A failed resend must preserve previous success.
            expectSendFailure(failingDispatcher, smtpFailure);

            Invoice resent = loadInvoice(factory);

            check(resent.isSent(),
                "Failed resend erased the previous successful status.");
            check(resent.getLastSendAttemptAt() != null,
                "Failed resend has no attempt timestamp.");

            check(sendCount.get() == 3,
                "Expected exactly three sending attempts.");

            System.out.println(
                "PASS: failed resend preserved previous success."
            );

            // 4. An unknown invoice must never reach the sender.
            try {
                successfulDispatcher.sendInvoice(MISSING_INVOICE_ID);
                throw new AssertionError(
                    "Expected rejection of a missing invoice."
                );
            } catch (IllegalArgumentException expected) {
                check(sendCount.get() == 3,
                    "Sender was called for a missing invoice.");
            }

            System.out.println(
                "PASS: missing invoice was rejected before sending."
            );
        } finally {
            deleteFixtures(factory, chargeId);
        }

        System.out.println("All invoice dispatcher tests passed.");
    }
    // ---------------------------------
    private static void testConcurrentSending(ConnectionFactory factory)
        throws Exception {

        int chargeId = createFixtures(factory);

        CountDownLatch firstSenderEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSender = new CountDownLatch(1);
        CountDownLatch secondLockAttempted = new CountDownLatch(1);
        CountDownLatch secondSenderEntered = new CountDownLatch(1);

        AtomicBoolean secondSawSent = new AtomicBoolean(false);

        RuntimeException sendFailure =
            new IllegalStateException("Simulated concurrent send failure.");

        InvoiceSender firstSender = invoice -> {
            check(!invoice.isSent(),
                "Concurrency test must start with an unsent invoice.");

            firstSenderEntered.countDown();

            try {
                if (!releaseFirstSender.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError(
                        "Timed out waiting to release the first sender."
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "First sender was interrupted.", exception
                );
            }
        };

        InvoiceSender secondSender = invoice -> {
            secondSawSent.set(invoice.isSent());
            secondSenderEntered.countDown();
            throw sendFailure;
        };

        // Observe when the second dispatcher attempts its locking query.
        ConnectionFactory observedFactory = new ConnectionFactory() {
            @Override
            public Connection openConnection() throws SQLException {
                return observeInvoiceLock(
                    factory.openConnection(), secondLockAttempted
                );
            }
        };

        try {
            check(!loadInvoice(factory).isSent(),
                "Fixture invoice must initially be unsent.");

            try (ExecutorService executor =
                    Executors.newFixedThreadPool(2)) {

                try {
                    Future<?> first = executor.submit(() ->
                        new JdbcInvoiceDispatcher(factory, firstSender)
                            .sendInvoice(INVOICE_ID)
                    );

                    check(firstSenderEntered.await(10, TimeUnit.SECONDS),
                        "First dispatch did not reach its sender.");

                    Future<?> second = executor.submit(() ->
                        expectSendFailure(
                            new JdbcInvoiceDispatcher(
                                observedFactory, secondSender
                            ),
                            sendFailure
                        )
                    );

                    check(secondLockAttempted.await(10, TimeUnit.SECONDS),
                        "Second dispatch did not attempt the invoice lock.");

                    check(
                        !secondSenderEntered.await(1, TimeUnit.SECONDS),
                        "Second sender ran before the first transaction finished."
                    );

                    releaseFirstSender.countDown();

                    first.get(30, TimeUnit.SECONDS);
                    second.get(30, TimeUnit.SECONDS);

                    check(secondSenderEntered.getCount() == 0,
                        "Second dispatch never reached its sender.");

                    check(secondSawSent.get(),
                        "Second dispatch loaded stale unsent state.");

                    Invoice stored = loadInvoice(factory);

                    check(stored.isSent(),
                        "Failed concurrent send erased the successful status.");

                    check(stored.getLastSendAttemptAt() != null,
                        "Send attempt timestamp is missing.");

                    System.out.println(
                        "PASS: concurrent failed send preserves successful status."
                    );
                } finally {
                    // Release the worker even if an assertion fails.
                    releaseFirstSender.countDown();
                }
            }
        } finally {
            deleteFixtures(factory, chargeId);
        }
    }

    private static Connection observeInvoiceLock(
            Connection realConnection,
            CountDownLatch lockAttempted) {

        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                try {
                    Object result = method.invoke(realConnection, args);

                    if (method.getName().equals("prepareStatement")
                            && args != null
                            && args.length > 0
                            && args[0] instanceof String sql) {

                        String normalized = sql.replaceAll("\\s+", " ")
                            .toUpperCase(java.util.Locale.ROOT);

                        if (normalized.contains("FROM INVOICES")
                                && normalized.contains("FOR UPDATE")) {

                            PreparedStatement realStatement =
                                (PreparedStatement) result;

                            return Proxy.newProxyInstance(
                                PreparedStatement.class.getClassLoader(),
                                new Class<?>[] { PreparedStatement.class },
                                (statementProxy, statementMethod, statementArgs) -> {
                                    if (statementMethod.getName()
                                            .equals("executeQuery")) {
                                        lockAttempted.countDown();
                                    }

                                    try {
                                        return statementMethod.invoke(
                                            realStatement, statementArgs
                                        );
                                    } catch (InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                }
                            );
                        }
                    }

                    return result;
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }
        );
    }
    private static int createFixtures(ConnectionFactory factory)
        throws SQLException {

    try (Connection connection = factory.openConnection()) {
        connection.setAutoCommit(false);

        try {
            requireUnused(connection, "students", STUDENT_ID);
            requireUnused(connection, "staff", STAFF_ID);
            requireUnused(connection, "items", ITEM_ID);
            requireUnused(connection, "loans", LOAN_ID);
            requireUnused(connection, "invoices", INVOICE_ID);
            requireUnused(connection, "invoices", MISSING_INVOICE_ID);

            Student student = new Student(
                "Dispatcher Test Student",
                true,
                "dispatcher-test-2000000100@example.com",
                STUDENT_ID
            );

            Staff staff = new Staff("Dispatcher Test Staff", STAFF_ID);

            Item item = new Item(
                ItemType.LAPTOP,
                ITEM_ID,
                "Dispatcher Test Laptop",
                ItemCondition.GOOD,
                "Test shelf",
                ItemStatus.IN_STOCK
            );

            Loan loan = new Loan(
                LOAN_ID, student, item, staff,
                LocalDate.of(2026, 10, 1)
            );

            StudentCharge charge = new StudentCharge(
                loan,
                ChargeType.LATE_FEE,
                new BigDecimal("15.00"),
                "Dispatcher test only."
            );

            new JdbcStudentRepository(connection).save(student);
            new JdbcStaffRepository(connection).save(staff);

            item.markAsLoaned();
            new JdbcItemRepository(connection).save(item);
            new JdbcLoanRepository(connection).save(loan);
            new JdbcChargeRepository(connection).save(charge);

            check(charge.getId() != null && charge.getId() > 0,
                "Charge was not assigned a generated ID.");

            int chargeId = charge.getId();

            Invoice invoice = new Invoice(
                INVOICE_ID, charge, LocalDate.of(2026, 10, 12)
            );
            new JdbcInvoiceRepository(connection).save(invoice);

            connection.commit();
            return chargeId;
        } catch (SQLException | RuntimeException | AssertionError failure) {
            rollback(connection, failure);
            throw failure;
        }
    }
}

    private static Invoice loadInvoice(ConnectionFactory factory)
            throws SQLException {

        // A fresh connection verifies that the dispatcher committed.
        try (Connection connection = factory.openConnection()) {
            return new JdbcInvoiceRepository(connection)
                .findById(INVOICE_ID)
                .orElseThrow(() -> new AssertionError(
                    "Test invoice was not found."
                ));
        }
    }

    private static void expectSendFailure(
            JdbcInvoiceDispatcher dispatcher,
            RuntimeException expectedCause) {

        try {
            dispatcher.sendInvoice(INVOICE_ID);
            throw new AssertionError("Expected InvoiceSendException.");
        } catch (InvoiceSendException exception) {
            check(exception.getCause() == expectedCause,
                "Original sending failure was not preserved.");
        }
    }

    private static void deleteFixtures(
        ConnectionFactory factory, int chargeId)
        throws SQLException {

    try (Connection connection = factory.openConnection()) {
        connection.setAutoCommit(false);

        try {
            int studentMemberId =
                findMemberId(connection, "students", STUDENT_ID);
            int staffMemberId =
                findMemberId(connection, "staff", STAFF_ID);

            deleteById(connection, "invoices", INVOICE_ID);
            deleteById(connection, "student_charges", chargeId);
            deleteById(connection, "loans", LOAN_ID);
            deleteById(connection, "items", ITEM_ID);
            deleteById(connection, "students", STUDENT_ID);
            deleteById(connection, "staff", STAFF_ID);
            deleteById(connection, "members", studentMemberId);
            deleteById(connection, "members", staffMemberId);

            connection.commit();
            System.out.println("PASS: test data cleaned up.");
        } catch (SQLException | RuntimeException failure) {
            rollback(connection, failure);
            throw failure;
        }
    }
}

    private static int findMemberId(
            Connection connection, String table, int id)
            throws SQLException {

        String sql = "SELECT member_id FROM " + table + " WHERE id = ?";

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                        "Missing test record in " + table
                    );
                }
                return result.getInt("member_id");
            }
        }
    }

    private static void requireUnused(
            Connection connection, String table, int id)
            throws SQLException {

        String sql = "SELECT id FROM " + table + " WHERE id = ?";

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet result = statement.executeQuery()) {
                check(!result.next(),
                    "Test ID already exists in " + table + ": " + id);
            }
        }
    }

    private static void deleteById(
            Connection connection, String table, int id)
            throws SQLException {

        // Table names come only from constants in this test.
        String sql = "DELETE FROM " + table + " WHERE id = ?";

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private static void rollback(
            Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}