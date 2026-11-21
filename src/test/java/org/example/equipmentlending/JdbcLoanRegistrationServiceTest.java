package org.example.equipmentlending;

import java.math.BigDecimal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.LocalDate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.repository.*;
import org.example.equipmentlending.service.JdbcLoanRegistrationService;
import org.example.equipmentlending.service.LoanRegistrationService;


public class JdbcLoanRegistrationServiceTest {
    private static final int STUDENT_ID = 2_000_000_200;
    private static final int STAFF_ID = 2_000_000_201;
    private static final int ITEM_ID = 2_000_000_202;
    private static final int HISTORY_ITEM_1 = 2_000_000_203;
    private static final int HISTORY_ITEM_2 = 2_000_000_204;
    private static final int NEW_LOAN_ID = 2_000_000_205;
    private static final int HISTORY_LOAN_1 = 2_000_000_206;
    private static final int HISTORY_LOAN_2 = 2_000_000_207;
    private static final int CHARGE_ID = 2_000_000_208;

    private static final LocalDate BORROW_DATE =
        LocalDate.of(2026, 11, 20);
private static Connection wrapConnection(
        Connection realConnection,
        SQLException simulatedFailure,
        AtomicBoolean failureTriggered,
        AtomicBoolean rollbackCalled) {

    return (Connection) Proxy.newProxyInstance(
        Connection.class.getClassLoader(),
        new Class<?>[] { Connection.class },
        (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement")
                    && args != null
                    && args.length > 0
                    && args[0] instanceof String sql) {

                PreparedStatement realStatement =
                    (PreparedStatement) invokeReal(
                        method, realConnection, args
                    );

                String normalizedSql = sql.strip()
                    .replaceAll("\\s+", " ")
                    .toUpperCase(java.util.Locale.ROOT);

                boolean writesItem =
                    normalizedSql.startsWith("INSERT INTO ITEMS")
                    || normalizedSql.startsWith("UPDATE ITEMS");

                if (writesItem) {
                    return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] { PreparedStatement.class },
                        (statementProxy, statementMethod, statementArgs) -> {
                            if (statementMethod.getName()
                                    .equals("executeUpdate")) {

                                // Prove the loan INSERT already happened.
                                check(
                                    new JdbcLoanRepository(realConnection)
                                        .findById(NEW_LOAN_ID).isPresent(),
                                    "Loan must exist inside the transaction "
                                        + "before the item save fails."
                                );

                                failureTriggered.set(true);
                                throw simulatedFailure;
                            }

                            return invokeReal(
                                statementMethod,
                                realStatement,
                                statementArgs
                            );
                        }
                    );
                }

                return realStatement;
            }

            Object result = invokeReal(method, realConnection, args);

            if (method.getName().equals("rollback")) {
                rollbackCalled.set(true);
            }

            return result;
        }
    );
}

private static Object invokeReal(
        Method method,
        Object target,
        Object[] args) throws Throwable {

    try {
        return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
        // Preserve the actual JDBC exception.
        throw exception.getCause();
    }
}
    private static boolean attemptConcurrentRegistration(
        ConnectionFactory factory,
        int loanId,
        int studentId,
        CountDownLatch ready,
        CountDownLatch start) throws InterruptedException {

    ready.countDown();

    if (!start.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting to start registration.");
    }

    try {
        new JdbcLoanRegistrationService(factory).registerLoan(
            loanId,
            studentId,
            ITEM_ID,
            STAFF_ID,
            BORROW_DATE
        );

        return true;
    } catch (IllegalStateException exception) {
        if ("Item not available.".equals(exception.getMessage())) {
            return false;
        }

        throw exception;
    }
}
private static boolean attemptConcurrentStudentRegistration(
        ConnectionFactory factory,
        int loanId,
        int itemId,
        CountDownLatch ready,
        CountDownLatch start) throws InterruptedException {

    ready.countDown();

    if (!start.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting to start registration.");
    }

    try {
        new JdbcLoanRegistrationService(factory).registerLoan(
            loanId,
            STUDENT_ID,
            itemId,
            STAFF_ID,
            BORROW_DATE
        );

        return true;
    } catch (IllegalStateException exception) {
        if ("Student already has two active loans."
                .equals(exception.getMessage())) {
            return false;
        }

        throw exception;
    }
}
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        runScenario(factory, "valid", () -> {
            Loan loan = register(factory, NEW_LOAN_ID);

            check(loan.getId() == NEW_LOAN_ID, "Wrong returned loan ID.");

            try (Connection connection = factory.openConnection()) {
                Loan stored = new JdbcLoanRepository(connection)
                    .findById(NEW_LOAN_ID)
                    .orElseThrow(() -> new AssertionError(
                        "Loan was not committed."
                    ));

                check(stored.getStudent().getId() == STUDENT_ID,
                    "Wrong student.");
                check(stored.getItem().getId() == ITEM_ID,
                    "Wrong item.");
                check(stored.getRegisteredBy().getId() == STAFF_ID,
                    "Wrong registering staff.");
                check(stored.getBorrowDate().equals(BORROW_DATE),
                    "Wrong borrowing date.");
                check(stored.getStatus() == LoanStatus.ACTIVE,
                    "New loan should be active.");

                check(loadItem(connection).getStatus() == ItemStatus.ON_LOAN,
                    "Item status was not committed.");
            }
        });

        runScenario(factory, "inactive", () ->
            expectRejected(factory, NEW_LOAN_ID,
                "Student is not active.")
        );

        runScenario(factory, "unavailable", () ->
            expectRejected(factory, NEW_LOAN_ID,
                "Item not available.")
        );

        runScenario(factory, "overdue", () ->
            expectRejected(factory, NEW_LOAN_ID,
                "Student has an overdue loan.")
        );

        runScenario(factory, "unpaid", () ->
            expectRejected(factory, NEW_LOAN_ID,
                "Student has an unpaid charge.")
        );

        runScenario(factory, "limit", () ->
            expectRejected(factory, NEW_LOAN_ID,
                "Student already has two active loans.")
        );

        runScenario(factory, "duplicate", () -> {
            try {
                register(factory, HISTORY_LOAN_1);
                throw new AssertionError("Existing loan ID was accepted.");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().toLowerCase()
                        .contains("already exists"),
                    "Unexpected rejection: " + expected.getMessage());
            }

            try (Connection connection = factory.openConnection()) {
                Loan original = new JdbcLoanRepository(connection)
                    .findById(HISTORY_LOAN_1)
                    .orElseThrow();

                check(original.getItem().getId() == HISTORY_ITEM_1,
                    "Existing loan was overwritten.");
                check(loadItem(connection).isAvailable(),
                    "Rejected registration changed the item.");
                check(new JdbcLoanRepository(connection)
                        .findByStudentId(STUDENT_ID).size() == 1,
                    "Rejected registration created another loan.");
            }
        });

        runScenario(factory, "rollback", () ->
            testRollbackAfterItemSaveFailure(factory)
        );
        runScenario(factory, "insert-duplicate", () -> {
    try (Connection connection = factory.openConnection()) {
        connection.setAutoCommit(false);

        try {
            JdbcLoanRepository repository =
                new JdbcLoanRepository(connection);

            Student student = new JdbcStudentRepository(connection)
                .findById(STUDENT_ID).orElseThrow();

            Staff staff = new JdbcStaffRepository(connection)
                .findById(STAFF_ID).orElseThrow();

            Item item = loadItem(connection);

            Loan original = new Loan(
                NEW_LOAN_ID, student, item, staff, BORROW_DATE
            );
            repository.insert(original);

            // Same ID, different date: must fail, not overwrite.
            Loan duplicate = new Loan(
                NEW_LOAN_ID, student, item, staff,
                BORROW_DATE.plusDays(1)
            );

            try {
                repository.insert(duplicate);
                throw new AssertionError("Duplicate insert was accepted.");
            } catch (IllegalStateException expected) {
                check(
                    expected.getCause() instanceof SQLException
                        && ((SQLException) expected.getCause())
                            .getErrorCode() == 1062,
                    "Expected a MySQL duplicate-key failure."
                );
            }

            Loan stored = repository.findById(NEW_LOAN_ID)
                .orElseThrow();

            check(stored.getBorrowDate().equals(BORROW_DATE),
                "Duplicate insert overwrote the original borrowing date.");
            check(stored.getDueDate().equals(original.getDueDate()),
                "Duplicate insert changed the original due date.");
            check(repository.findByStudentId(STUDENT_ID).size() == 1,
                "Expected exactly one loan.");
        } finally {
            connection.rollback();
        }
    }
});
        runScenario(factory, "concurrent-item", () -> {
    int secondStudentId = 2_000_000_209;
    int secondStudentMemberId;

    // Create and commit the second student so both connections can see it.
    try (Connection connection = factory.openConnection()) {
        connection.setAutoCommit(false);

        try {
            requireUnused(connection, "students", secondStudentId);

            Student secondStudent = new Student(
                "Second Concurrent Student",
                true,
                "concurrent-student-2000000209@example.com",
                secondStudentId
            );

            new JdbcStudentRepository(connection).save(secondStudent);

            secondStudentMemberId = findMemberId(
                connection, "students", secondStudentId
            );

            connection.commit();
        } catch (SQLException | RuntimeException | AssertionError failure) {
            rollback(connection, failure);
            throw failure;
        }
    }

    try {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        boolean firstSucceeded;
        boolean secondSucceeded;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() ->
                attemptConcurrentRegistration(
                    factory,
                    NEW_LOAN_ID,
                    STUDENT_ID,
                    ready,
                    start
                )
            );
            Future<Boolean> second = executor.submit(() ->
                attemptConcurrentRegistration(
                    factory,
                    HISTORY_LOAN_1,
                    secondStudentId,
                    ready,
                    start
                )
            );

            try {
                check(ready.await(10, TimeUnit.SECONDS),
                    "Both registration tasks did not become ready.");
            } finally {
                start.countDown();
            }

            firstSucceeded = first.get(30, TimeUnit.SECONDS);
            secondSucceeded = second.get(30, TimeUnit.SECONDS);
        }

        check(firstSucceeded != secondSucceeded,
            "Exactly one registration must succeed.");

        // Verify committed results through a new connection.
        try (Connection connection = factory.openConnection()) {
            JdbcLoanRepository loans = new JdbcLoanRepository(connection);

            check(loans.findById(NEW_LOAN_ID).isPresent() == firstSucceeded,
                "First loan persistence does not match its result.");

            check(loans.findById(HISTORY_LOAN_1).isPresent()
                    == secondSucceeded,
                "Second loan persistence does not match its result.");

            int winningLoanId =
                firstSucceeded ? NEW_LOAN_ID : HISTORY_LOAN_1;

            int winningStudentId =
                firstSucceeded ? STUDENT_ID : secondStudentId;

            Loan winner = loans.findById(winningLoanId).orElseThrow();

            check(winner.getStudent().getId() == winningStudentId,
                "Winning loan belongs to the wrong student.");

            check(winner.getItem().getId() == ITEM_ID,
                "Winning loan references the wrong item.");

            check(winner.getStatus() == LoanStatus.ACTIVE,
                "Winning loan should be active.");

            check(loadItem(connection).getStatus() == ItemStatus.ON_LOAN,
                "Item should be marked ON_LOAN.");

            String sql = """
                SELECT COUNT(*)
                FROM loans
                WHERE item_id = ? AND status = 'ACTIVE'
                """;

            try (PreparedStatement statement =
                    connection.prepareStatement(sql)) {
                statement.setInt(1, ITEM_ID);

                try (ResultSet result = statement.executeQuery()) {
                    check(result.next(), "Count query returned no result.");
                    check(result.getInt(1) == 1,
                        "The same item has multiple active loans.");
                }
            }
        }
    } finally {
        // Remove both possible loans before removing the second student.
        // runScenario cleans up the remaining fixtures.
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                deleteById(connection, "loans", NEW_LOAN_ID);
                deleteById(connection, "loans", HISTORY_LOAN_1);
                deleteById(connection, "students", secondStudentId);
                deleteById(connection, "members", secondStudentMemberId);

                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }
});
        runScenario(factory, "duplicate", () -> {
    // The fixture already has one active loan on HISTORY_ITEM_1.
    // ITEM_ID is available; create a second available item.
    try (Connection connection = factory.openConnection()) {
        connection.setAutoCommit(false);

        try {
            Item secondItem = new Item(
                ItemType.LAPTOP,
                HISTORY_ITEM_2,
                "Second Available Test Laptop",
                ItemCondition.GOOD,
                "Test shelf",
                ItemStatus.IN_STOCK
            );

            new JdbcItemRepository(connection).save(secondItem);
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            rollback(connection, failure);
            throw failure;
        }
    }

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    boolean firstSucceeded;
    boolean secondSucceeded;

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        Future<Boolean> first = executor.submit(() ->
            attemptConcurrentStudentRegistration(
                factory, NEW_LOAN_ID, ITEM_ID, ready, start
            )
        );

        Future<Boolean> second = executor.submit(() ->
            attemptConcurrentStudentRegistration(
                factory, HISTORY_LOAN_2, HISTORY_ITEM_2, ready, start
            )
        );

        try {
            check(ready.await(10, TimeUnit.SECONDS),
                "Both registration tasks did not become ready.");
        } finally {
            start.countDown();
        }

        firstSucceeded = first.get(30, TimeUnit.SECONDS);
        secondSucceeded = second.get(30, TimeUnit.SECONDS);
    }

    check(firstSucceeded != secondSucceeded,
        "Exactly one additional registration must succeed.");

    try (Connection connection = factory.openConnection()) {
        JdbcLoanRepository loans = new JdbcLoanRepository(connection);
        JdbcItemRepository items = new JdbcItemRepository(connection);

        long activeCount = loans.findByStudentId(STUDENT_ID)
            .stream()
            .filter(loan -> loan.getStatus() == LoanStatus.ACTIVE)
            .count();

        check(activeCount == 2,
            "Student must finish with exactly two active loans.");

        check(loans.findById(NEW_LOAN_ID).isPresent() == firstSucceeded,
            "First loan persistence does not match its result.");

        check(loans.findById(HISTORY_LOAN_2).isPresent() == secondSucceeded,
            "Second loan persistence does not match its result.");

        int winningItemId =
            firstSucceeded ? ITEM_ID : HISTORY_ITEM_2;

        int losingItemId =
            firstSucceeded ? HISTORY_ITEM_2 : ITEM_ID;

        check(items.findById(winningItemId).orElseThrow()
                .getStatus() == ItemStatus.ON_LOAN,
            "Winning request's item must be ON_LOAN.");

        check(items.findById(losingItemId).orElseThrow().isAvailable(),
            "Rejected request's item must remain available.");

        check(loans.findById(HISTORY_LOAN_1).orElseThrow()
                .getItem().getId() == HISTORY_ITEM_1,
            "Original loan was changed.");
    }

    System.out.println("PASS: concurrent student loan limit.");
});
runScenario(factory, "wrapper-rollback", () -> {
    AtomicBoolean failureTriggered = new AtomicBoolean(false);
    AtomicBoolean rollbackCalled = new AtomicBoolean(false);

    SQLException simulatedFailure =
        new SQLException("Simulated item save failure.");

    ConnectionFactory failingFactory = new ConnectionFactory() {
        @Override
        public Connection openConnection() throws SQLException {
            Connection realConnection = factory.openConnection();

            return wrapConnection(
                realConnection,
                simulatedFailure,
                failureTriggered,
                rollbackCalled
            );
        }
    };

    try {
        new JdbcLoanRegistrationService(failingFactory).registerLoan(
            NEW_LOAN_ID,
            STUDENT_ID,
            ITEM_ID,
            STAFF_ID,
            BORROW_DATE
        );

        throw new AssertionError("Expected item save failure.");
    } catch (IllegalStateException failure) {
        // Find the original SQL error through any exception wrappers.
        Throwable cause = failure;

        while (cause != null && cause != simulatedFailure) {
            cause = cause.getCause();
        }

        check(cause == simulatedFailure,
            "Expected the simulated item save failure.");
    }

    check(failureTriggered.get(),
        "The test never reached the item save.");

    check(rollbackCalled.get(),
        "JdbcLoanRegistrationService did not explicitly roll back.");

    // Read committed state through a separate, normal connection.
    try (Connection connection = factory.openConnection()) {
        check(new JdbcLoanRepository(connection)
                .findById(NEW_LOAN_ID).isEmpty(),
            "The loan insert was not rolled back.");

        check(loadItem(connection).isAvailable(),
            "The item should remain available after rollback.");
    }
});
        System.out.println("All loan registration tests passed.");
    }

    private static Loan register(ConnectionFactory factory, int loanId) {
        return new JdbcLoanRegistrationService(factory).registerLoan(
            loanId, STUDENT_ID, ITEM_ID, STAFF_ID, BORROW_DATE
        );
    }

    private static void expectRejected(
            ConnectionFactory factory,
            int loanId,
            String expectedMessage) throws SQLException {

        try {
            register(factory, loanId);
            throw new AssertionError("Registration should have failed.");
        } catch (IllegalStateException expected) {
            check(expectedMessage.equals(expected.getMessage()),
                "Unexpected failure: " + expected.getMessage());
        }

        try (Connection connection = factory.openConnection()) {
            check(new JdbcLoanRepository(connection)
                    .findById(loanId).isEmpty(),
                "Rejected registration saved a loan.");

            Item item = loadItem(connection);
            check(item.getStatus() == ItemStatus.IN_STOCK,
                "Rejected registration changed the item status.");
        }
    }

    private static void testRollbackAfterItemSaveFailure(
            ConnectionFactory factory) throws SQLException {

        RuntimeException simulatedFailure =
            new IllegalStateException("Simulated item save failure.");

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            JdbcLoanRepository loans = new JdbcLoanRepository(connection);
            JdbcItemRepository items = new JdbcItemRepository(connection);

            ItemRepository failingItems = new ItemRepository() {
                @Override
                public Optional<Item> findById(int id) {
                    return items.findById(id);
                }

                @Override
                public void save(Item item) {
                    // Prove the loan INSERT happened before this failure.
                    check(loans.findById(NEW_LOAN_ID).isPresent(),
                        "Expected loan to exist within the transaction.");

                    throw simulatedFailure;
                }
            };

            try {
                Student student = new JdbcStudentRepository(connection)
                    .findById(STUDENT_ID).orElseThrow();

                Staff staff = new JdbcStaffRepository(connection)
                    .findById(STAFF_ID).orElseThrow();

                LoanRegistrationService service =
                    new LoanRegistrationService(
                        loans,
                        new JdbcChargeRepository(connection),
                        failingItems
                    );

                service.registerLoan(
                    NEW_LOAN_ID, student, loadItem(connection),
                    staff, BORROW_DATE
                );

                throw new AssertionError("Expected item save failure.");
            } catch (RuntimeException failure) {
                check(failure == simulatedFailure,
                    "Unexpected failure instead of item save failure.");
            } finally {
                connection.rollback();
            }
        }

        try (Connection connection = factory.openConnection()) {
            check(new JdbcLoanRepository(connection)
                    .findById(NEW_LOAN_ID).isEmpty(),
                "Rollback did not remove the new loan.");
            check(loadItem(connection).isAvailable(),
                "Item should remain available after rollback.");
        }
    }

    @FunctionalInterface
    private interface TestAction {
        void run() throws Exception;
    }

    private static void runScenario(
            ConnectionFactory factory,
            String scenario,
            TestAction test) throws Exception {

        createFixtures(factory, scenario);

        try {
            test.run();
            System.out.println("PASS: " + scenario);
        } finally {
            deleteFixtures(factory);
        }
    }

    private static void createFixtures(
            ConnectionFactory factory, String scenario)
            throws SQLException {

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                requireUnused(connection, "students", STUDENT_ID);
                requireUnused(connection, "staff", STAFF_ID);
                requireUnused(connection, "items", ITEM_ID);
                requireUnused(connection, "items", HISTORY_ITEM_1);
                requireUnused(connection, "items", HISTORY_ITEM_2);
                requireUnused(connection, "loans", NEW_LOAN_ID);
                requireUnused(connection, "loans", HISTORY_LOAN_1);
                requireUnused(connection, "loans", HISTORY_LOAN_2);
                requireUnused(connection, "student_charges", CHARGE_ID);

                Student student = new Student(
                    "Registration Test Student",
                    !scenario.equals("inactive"),
                    "registration-test-2000000200@example.com",
                    STUDENT_ID
                );

                Staff staff = new Staff("Registration Test Staff", STAFF_ID);

                Item item = new Item(
                    ItemType.LAPTOP,
                    ITEM_ID,
                    "Registration Test Laptop",
                    scenario.equals("unavailable")
                        ? ItemCondition.PHYSICAL_DAMAGE
                        : ItemCondition.GOOD,
                    "Test shelf",
                    ItemStatus.IN_STOCK
                );

                new JdbcStudentRepository(connection).save(student);
                new JdbcStaffRepository(connection).save(staff);
                new JdbcItemRepository(connection).save(item);

                boolean needsHistory =
                    scenario.equals("overdue")
                    || scenario.equals("unpaid")
                    || scenario.equals("limit")
                    || scenario.equals("duplicate");

                if (needsHistory) {
                    LocalDate previousBorrowDate =
                        scenario.equals("overdue")
                            ? BORROW_DATE.minusDays(30)
                            : BORROW_DATE;

                    Loan previous = saveHistoricalLoan(
                        connection, student, staff,
                        HISTORY_ITEM_1, HISTORY_LOAN_1,
                        previousBorrowDate
                    );

                    if (scenario.equals("unpaid")) {
                        StudentCharge charge = new StudentCharge(
                            CHARGE_ID,
                            previous,
                            ChargeType.DAMAGE,
                            new BigDecimal("15.00"),
                            "Unpaid charge test fixture."
                        );
                        new JdbcChargeRepository(connection).save(charge);
                    }

                    if (scenario.equals("limit")) {
                        saveHistoricalLoan(
                            connection, student, staff,
                            HISTORY_ITEM_2, HISTORY_LOAN_2,
                            BORROW_DATE
                        );
                    }
                }

                connection.commit();
            } catch (SQLException | RuntimeException | AssertionError failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private static Loan saveHistoricalLoan(
            Connection connection,
            Student student,
            Staff staff,
            int itemId,
            int loanId,
            LocalDate borrowDate) {

        Item item = new Item(
            ItemType.LAPTOP, itemId, "Historical Test Laptop",
            ItemCondition.GOOD, "Test shelf", ItemStatus.IN_STOCK
        );

        Loan loan = new Loan(
            loanId, student, item, staff, borrowDate
        );

        item.markAsLoaned();
        new JdbcItemRepository(connection).save(item);
        new JdbcLoanRepository(connection).save(loan);

        return loan;
    }

    private static Item loadItem(Connection connection) {
        return new JdbcItemRepository(connection)
            .findById(ITEM_ID)
            .orElseThrow(() -> new AssertionError("Test item missing."));
    }

    private static void deleteFixtures(ConnectionFactory factory)
            throws SQLException {

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                int studentMemberId =
                    findMemberId(connection, "students", STUDENT_ID);
                int staffMemberId =
                    findMemberId(connection, "staff", STAFF_ID);

                deleteById(connection, "student_charges", CHARGE_ID);
                deleteById(connection, "loans", NEW_LOAN_ID);
                deleteById(connection, "loans", HISTORY_LOAN_1);
                deleteById(connection, "loans", HISTORY_LOAN_2);
                deleteById(connection, "items", ITEM_ID);
                deleteById(connection, "items", HISTORY_ITEM_1);
                deleteById(connection, "items", HISTORY_ITEM_2);
                deleteById(connection, "students", STUDENT_ID);
                deleteById(connection, "staff", STAFF_ID);
                deleteById(connection, "members", studentMemberId);
                deleteById(connection, "members", staffMemberId);

                connection.commit();
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

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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

        // Table names are supplied only by this test.
        String sql = "DELETE FROM " + table + " WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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