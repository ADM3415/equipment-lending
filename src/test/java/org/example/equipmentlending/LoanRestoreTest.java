package org.example.equipmentlending;

import java.time.LocalDate;
import org.example.equipmentlending.model.*;

public class LoanRestoreTest {
    public static void main(String[] args) {
        Student student = new Student("Test Student", true, "student@example.com", 1);
        Staff registeredBy = new Staff("Registering Staff", 2);
        Staff processedBy = new Staff("Processing Staff", 3);
        Teacher returnedBy = new Teacher("Test Teacher", 4);

        // The laptop has already been borrowed again.
        Item item = new Item(ItemType.LAPTOP, 5, "Laptop", ItemCondition.GOOD,
                "Shelf A", ItemStatus.ON_LOAN);

        LocalDate borrowed = LocalDate.of(2026, 10, 1);
        LocalDate due = borrowed.plusDays(10); // Deliberately different from the 7-day default.
        LocalDate returned = borrowed.plusDays(4);

        Loan loan = Loan.restore(6, student, item, registeredBy, borrowed, due,
                LoanStatus.RETURNED, returnedBy, processedBy, returned, null,
                ItemCondition.GOOD, null);

        check(loan.getStatus() == LoanStatus.RETURNED, "Wrong loan status.");
        check(due.equals(loan.getDueDate()), "Stored due date was overwritten.");
        check(returned.equals(loan.getReturnDate()), "Wrong return date.");
        check(loan.getReturnedBy() == returnedBy, "Wrong person recorded as returner.");
        check(loan.getReturnProcessedBy() == processedBy, "Wrong processing staff.");
        check(item.getStatus() == ItemStatus.ON_LOAN, "Restoring history changed the item.");

        System.out.println("PASS: restored a returned loan without changing the item.");
        try{
            Loan.restore(7, student, item, registeredBy, borrowed, due,
            LoanStatus.ACTIVE, null, null, returned, null, null, null);
            throw new AssertionError("An active loan with a return date was accepted.");
        } 
        catch (IllegalArgumentException expected) {
            System.out.println("PASS: rejected an active loan with a return date.");
        }
        LocalDate reportedLost = borrowed.plusDays(5);
        Loan lostLoan = Loan.restore(8, student, item, registeredBy, borrowed, due,
                LoanStatus.CLOSED_LOST, null, processedBy, null, reportedLost, null,
                "Laptop reported missing.");
        check(lostLoan.getStatus() == LoanStatus.CLOSED_LOST, "Wrong loan status.");
        check(reportedLost.equals(lostLoan.getLossReportedDate()), "Wrong loss report date.");
        check(lostLoan.getReturnProcessedBy() == processedBy, "Wrong staff member.");
        check(lostLoan.getReturnedBy() == null, "A lost item cannot have a returner.");
        check(lostLoan.getReturnDate() == null, "A lost item cannot have a return date.");

        System.out.println("PASS: restored a lost loan without recording a return.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}