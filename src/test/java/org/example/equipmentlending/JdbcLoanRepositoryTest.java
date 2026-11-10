package org.example.equipmentlending;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.*;
import org.example.equipmentlending.repository.*;

public class JdbcLoanRepositoryTest {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                runTest(connection);
            } finally {
                connection.rollback();
            }

            System.out.println("Test transaction rolled back.");
        }
    }

    private static void runTest(Connection connection) throws Exception {
        int studentId = 2_000_000_010;
        int itemId = 2_000_000_011;
        int registeringStaffId = 2_000_000_012;
        int processingStaffId = 2_000_000_013;
        int teacherId = 2_000_000_014;
        int loanId = 2_000_000_015;

        JdbcStudentRepository students = new JdbcStudentRepository(connection);
        JdbcItemRepository items = new JdbcItemRepository(connection);
        JdbcStaffRepository staff = new JdbcStaffRepository(connection);
        JdbcTeacherRepository teachers = new JdbcTeacherRepository(connection);
        JdbcLoanRepository loans = new JdbcLoanRepository(connection);

        // Avoid updating any existing records.
        check(students.findById(studentId).isEmpty(), "Student test ID is in use.");
        check(items.findById(itemId).isEmpty(), "Item test ID is in use.");
        check(staff.findById(registeringStaffId).isEmpty(),
                "Registering staff test ID is in use.");
        check(staff.findById(processingStaffId).isEmpty(),
                "Processing staff test ID is in use.");
        check(teachers.findById(teacherId).isEmpty(), "Teacher test ID is in use.");

        students.save(new Student(
                "Loan Test Student", true, "loan-test@example.com", studentId
        ));
        staff.save(new Staff("Registering Staff", registeringStaffId));
        staff.save(new Staff("Processing Staff", processingStaffId));
        teachers.save(new Teacher("Returning Teacher", teacherId));

        // The item has since been borrowed again.
        items.save(new Item(
                ItemType.LAPTOP, itemId, "Test Laptop",
                ItemCondition.GOOD, "Shelf A", ItemStatus.ON_LOAN
        ));

        check(loans.findByStudentId(studentId).isEmpty(),
                "A student without loans should have an empty history.");
        System.out.println("PASS: empty loan history.");

        LocalDate borrowed = LocalDate.of(2026, 10, 1);
        LocalDate due = borrowed.plusDays(7);
        LocalDate returned = borrowed.plusDays(4);

        // Load the related objects created by the test setup.
        Student student = students.findById(studentId).orElseThrow();
        Item item = items.findById(itemId).orElseThrow();
        Staff registeredBy = staff.findById(registeringStaffId).orElseThrow();
        Staff processedBy = staff.findById(processingStaffId).orElseThrow();
        Teacher returnedBy = teachers.findById(teacherId).orElseThrow();

        // Save a new ACTIVE loan through the repository.
        Loan loan = new Loan(
                loanId, student, item, registeredBy, borrowed
        );
        loans.save(loan);

        List<Loan> initialHistory = loans.findByStudentId(studentId);
        check(initialHistory.size() == 1, "Expected one newly saved loan.");

        Loan initialLoad = initialHistory.get(0);
        check(initialLoad.getId() == loanId, "Wrong loan ID.");
        check(initialLoad.getStatus() == LoanStatus.ACTIVE,
                "New loan should be ACTIVE.");
        check(initialLoad.getReturnedBy() == null,
                "Active loan should not have a returner.");
        check(initialLoad.getReturnProcessedBy() == null,
                "Active loan should not have processing staff.");
        check(initialLoad.getReturnDate() == null,
                "Active loan should not have a return date.");
        check(initialLoad.getLossReportedDate() == null,
                "Active loan should not have a loss report date.");
        check(initialLoad.getReturnCondition() == null,
                "Active loan should not have a return condition.");
        check(initialLoad.getReturnDescription() == null,
                "Active loan should not have a return description.");

        System.out.println("PASS: saved and loaded an ACTIVE loan with null return fields.");

        // Change the same loan to RETURNED and save it again.
        loan.markAsReturned(
                returnedBy,
                processedBy,
                returned,
                ItemCondition.GOOD,
                "Returned by a teacher."
        );
        loans.save(loan);

        List<Loan> history = loans.findByStudentId(studentId);
        check(history.size() == 1, "Expected exactly one loan.");

        Loan loaded = history.get(0);

        check(loaded.getId() == loanId, "Wrong loan ID.");
        check(loaded.getStudent().getId() == studentId, "Wrong student.");
        check(loaded.getItem().getId() == itemId, "Wrong item.");
        check(loaded.getRegisteredBy().getId() == registeringStaffId,
                "Wrong registering staff.");
        check(loaded.getStatus() == LoanStatus.RETURNED, "Wrong status.");
        check(borrowed.equals(loaded.getBorrowDate()), "Wrong borrow date.");
        check(due.equals(loaded.getDueDate()), "Stored due date was changed.");
        check(returned.equals(loaded.getReturnDate()), "Wrong return date.");

        check(loaded.getReturnedBy() != null, "Returner is missing.");
        check(loaded.getReturnedBy().getType() == MemberType.TEACHER,
                "Returner should be a teacher.");
        check(loaded.getReturnedBy().getId() == teacherId,
                "Wrong teacher ID.");
        check(loaded.getReturnProcessedBy() != null,
                "Processing staff is missing.");
        check(loaded.getReturnProcessedBy().getId() == processingStaffId,
                "Wrong processing staff.");

        check(loaded.getReturnCondition() == ItemCondition.GOOD,
                "Wrong return condition.");
        check("Returned by a teacher.".equals(loaded.getReturnDescription()),
                "Wrong return description.");
        check(loaded.getLossReportedDate() == null,
                "Loss report date should be null.");

        check(loaded.getItem().getStatus() == ItemStatus.ON_LOAN,
                "Loading history changed the item's Java state.");
        check(items.findById(itemId).orElseThrow().getStatus() == ItemStatus.ON_LOAN,
                "Loading history changed the item's database state.");

        System.out.println("PASS: restored loan fields and resolved the teacher correctly.");
        System.out.println("PASS: preserved the item's current status.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}