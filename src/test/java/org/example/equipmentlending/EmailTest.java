package org.example.equipmentlending;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.example.equipmentlending.model.*;
import org.example.equipmentlending.notification.InvoiceSender;
import org.example.equipmentlending.service.InvoiceService;

public class EmailTest {

    public static void main(String[] args) {
        Student student = new Student(
            "Test Student", true, "student@example.com", 1
        );

        Staff staff = new Staff("Test Staff", 1);

        Item item = new Item(
            ItemType.LAPTOP,
            1,
            "Test Laptop",
            ItemCondition.GOOD,
            "Room A — Shelf 1",
            ItemStatus.IN_STOCK
        );

        Loan loan = new Loan(
            1,
            student,
            item,
            staff,
            LocalDate.now().minusDays(10)
        );

        StudentCharge charge = new StudentCharge(
            1,
            loan,
            ChargeType.LATE_FEE,
            new BigDecimal("15.00"),
            "TEST ONLY — no real payment required."
        );

        Invoice invoice = new Invoice(1, charge, LocalDate.now());

        // First attempt: simulate success.
        InvoiceSender successfulSender = invoiceToSend -> {
            // Returning normally simulates successful sending.
        };

        InvoiceService successfulService =
            new InvoiceService(successfulSender);

        successfulService.sendInvoice(invoice);

        System.out.println("After success: " + invoice.isSent());
        System.out.println(
            "First attempt: " + invoice.getLastSendAttemptAt()
        );

        // Second attempt: simulate failure on the same invoice.
        InvoiceSender failingSender = invoiceToSend -> {
            throw new IllegalStateException("Simulated resend failure.");
        };

        InvoiceService failingService =
            new InvoiceService(failingSender);

        try {
            failingService.sendInvoice(invoice);
        } catch (IllegalStateException exception) {
            System.out.println(exception.getMessage());
        }

        System.out.println("After failed resend: " + invoice.isSent());
        System.out.println(
            "Latest attempt: " + invoice.getLastSendAttemptAt()
        );
    }
}