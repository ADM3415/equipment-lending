package org.example.equipmentlending;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.equipmentlending.model.*;
import org.example.equipmentlending.notification.InvoiceSender;
import org.example.equipmentlending.repository.ChargeRepository;
import org.example.equipmentlending.repository.CsvInvoiceRepository;
import org.example.equipmentlending.repository.InvoiceRepository;
import org.example.equipmentlending.service.InvoiceService;
import org.example.equipmentlending.service.InvoiceSendException;

public class EmailTest {

    public static void main(String[] args) throws Exception {
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
            1, student, item, staff, LocalDate.now().minusDays(10)
        );

        StudentCharge charge = new StudentCharge(
            1,
            loan,
            ChargeType.LATE_FEE,
            new BigDecimal("15.00"),
            "TEST ONLY — no real payment required."
        );

        // Provides the charge needed when loading invoices.
        ChargeRepository chargeRepository = new ChargeRepository() {
            @Override
            public Optional<StudentCharge> findById(int id) {
                return id == charge.getId()
                    ? Optional.of(charge)
                    : Optional.empty();
            }

            @Override
            public List<StudentCharge> findByStudentId(int studentId) {
                return charge.getLoan().getStudent().getId() == studentId
                    ? List.of(charge)
                    : List.of();
            }

            @Override
            public void save(StudentCharge chargeToSave) {
                throw new UnsupportedOperationException("Test lookup only.");
            }
        };

        // A separate temporary file for each test run.
        Path testFile = Files.createTempDirectory("invoice-test-").resolve("invoices.csv");

        InvoiceRepository repository =
            new CsvInvoiceRepository(testFile, chargeRepository);

        InvoiceSender successfulSender = invoice -> {
            // Simulate success without sending an email.
        };

        RuntimeException simulatedFailure =
            new IllegalStateException("Simulated SMTP failure.");

        InvoiceSender failingSender = invoice -> {
            throw simulatedFailure;
        };

        InvoiceService successfulService =
            new InvoiceService(successfulSender, repository);

        InvoiceService failingService =
            new InvoiceService(failingSender, repository);

        // 1. Successful sending must save the sent status and attempt time.
        Invoice successInvoice = new Invoice(1, charge, LocalDate.now());

        successfulService.sendInvoice(successInvoice);

        Invoice savedSuccess = repository.findById(1).orElseThrow();

        check(savedSuccess.isSent(), "Successful invoice must be marked sent.");
        check(
            successInvoice.getLastSendAttemptAt() != null
                && successInvoice.getLastSendAttemptAt()
                    .equals(savedSuccess.getLastSendAttemptAt()),
            "Successful attempt time must be saved."
        );

        System.out.println("PASS: successful sending is saved.");

        // 2. A failed first attempt must stay unsent, but save the attempt.
        Invoice failedInvoice = new Invoice(2, charge, LocalDate.now());

        expectFailure(failingService, failedInvoice, simulatedFailure);

        Invoice savedFailure = repository.findById(2).orElseThrow();

        check(!savedFailure.isSent(), "Failed first attempt must stay unsent.");
        check(
            failedInvoice.getLastSendAttemptAt() != null
                && failedInvoice.getLastSendAttemptAt()
                    .equals(savedFailure.getLastSendAttemptAt()),
            "Failed attempt time must be saved."
        );

        System.out.println("PASS: failed first attempt is saved.");

        // 3. A failed resend must preserve an earlier successful status.
        // Use an old timestamp so the update is clearly distinguishable.
        LocalDateTime oldAttempt = LocalDate.now()
            .minusDays(1)
            .atTime(12, 0);

        successInvoice.recordSendAttempt(oldAttempt);
        repository.save(successInvoice);

        expectFailure(failingService, successInvoice, simulatedFailure);

        Invoice savedResend = repository.findById(1).orElseThrow();

        check(savedResend.isSent(), "Failed resend must preserve sent status.");
        check(
            savedResend.getLastSendAttemptAt().isAfter(oldAttempt),
            "Failed resend must update the attempt time."
        );
        check(
            successInvoice.getLastSendAttemptAt()
                .equals(savedResend.getLastSendAttemptAt()),
            "Resend attempt time must be saved."
        );

        System.out.println("PASS: failed resend preserves previous success.");

        // 4. Save and reload an invoice with no sending attempt.
        Invoice csvInvoice = new Invoice(3, charge, LocalDate.now());
        repository.save(csvInvoice);

        Invoice firstLoad = repository.findById(3).orElseThrow();

        check(!firstLoad.isSent(), "New invoice must be unsent.");
        check(
            firstLoad.getLastSendAttemptAt() == null,
            "New invoice must have no attempt time."
        );
        check(
            firstLoad.getCharge().getId() == charge.getId()
                && firstLoad.getIssuedDate().equals(csvInvoice.getIssuedDate()),
            "Charge and issue date must be restored."
        );

        // Update the same ID instead of adding another row.
        csvInvoice.recordSendAttempt(LocalDateTime.now());
        csvInvoice.markAsSent();
        repository.save(csvInvoice);

        // Use a new repository instance to read the saved file.
        InvoiceRepository reloadedRepository =
            new CsvInvoiceRepository(testFile, chargeRepository);

        Invoice updated = reloadedRepository.findById(3).orElseThrow();

        check(updated.isSent(), "Updated sent status must be restored.");
        check(
            csvInvoice.getLastSendAttemptAt()
                .equals(updated.getLastSendAttemptAt()),
            "Updated attempt time must be restored."
        );

        check(
            Files.readAllLines(testFile, StandardCharsets.UTF_8).size() == 4,
            "Expected one header and three invoice rows, without duplicates."
        );

        check(
            reloadedRepository.findById(999).isEmpty(),
            "Unknown invoice ID must return Optional.empty()."
        );

        System.out.println("PASS: CSV save, update, and load.");
        System.out.println("All tests passed.");
        // 5. Both sending and saving fail.
        RuntimeException saveFailure = new IllegalStateException("Simulated repository save failure.");
        InvoiceRepository failingRepository = new InvoiceRepository(){
            @Override
            public void save(Invoice invoice) {
                throw saveFailure;
            }
            @Override
            public Optional<Invoice> findById(int id) {
                throw new UnsupportedOperationException("Not used in this test.");
            }
        };

        RuntimeException sendFailure =
            new IllegalStateException("Simulated email send failure.");

        InvoiceSender doubleFailureSender = invoice -> {
            throw sendFailure;
        };

        InvoiceService doubleFailureService =
            new InvoiceService(doubleFailureSender, failingRepository);

        Invoice doubleFailureInvoice =
            new Invoice(4, charge, LocalDate.now());

        try {
            doubleFailureService.sendInvoice(doubleFailureInvoice);
            throw new AssertionError("Expected saving to fail.");
        } catch (RuntimeException actual) {
            check(actual == saveFailure,
                "The saving failure must be the exception thrown.");

            Throwable[] suppressed = actual.getSuppressed();

            check(suppressed.length == 1 && suppressed[0] == sendFailure,
                "The sending failure must be suppressed on the saving failure.");

            check(sendFailure.getSuppressed().length == 0,
                "The exceptions must not reference each other circularly.");
        }

        check(!doubleFailureInvoice.isSent(),
            "Invoice must remain unsent when sending fails.");

        check(doubleFailureInvoice.getLastSendAttemptAt() != null,
            "Attempt time must still be recorded in memory.");

        System.out.println("PASS: saving failure preserves the sending failure.");


        // 6. Sending succeeds, but saving fails.
        // Use a fresh exception so suppressed errors from test 5 aren't reused.
        RuntimeException saveOnlyFailure =
            new IllegalStateException("Simulated save failure after sending.");

        InvoiceRepository saveOnlyFailingRepository = new InvoiceRepository() {
            @Override
            public void save(Invoice invoice) {
                throw saveOnlyFailure;
            }

            @Override
            public Optional<Invoice> findById(int id) {
                throw new UnsupportedOperationException("Not used in this test.");
            }
        };

        InvoiceService saveOnlyFailingService =
            new InvoiceService(successfulSender, saveOnlyFailingRepository);

        Invoice sentButNotSaved =
            new Invoice(5, charge, LocalDate.now());

        try {
            saveOnlyFailingService.sendInvoice(sentButNotSaved);
            throw new AssertionError("Expected saving to fail after sending.");
        } catch (RuntimeException actual) {
            check(actual == saveOnlyFailure,
                "The original saving failure must propagate.");

            check(actual.getSuppressed().length == 0,
                "There should be no sending failure attached.");
        }

        check(sentButNotSaved.isSent(),
            "Successful sending must be reflected in memory.");

        check(sentButNotSaved.getLastSendAttemptAt() != null,
            "Attempt time must be recorded in memory.");

        System.out.println("PASS: saving failure after successful sending propagates.");
        System.out.println("All tests passed.");
        System.out.println("Test file: " + testFile);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(InvoiceService service, Invoice invoice, RuntimeException expectedCause) {
        try {
            service.sendInvoice(invoice);
        } 
        catch (InvoiceSendException actual) {
            check(actual.getCause() == expectedCause,
                "InvoiceSendException must preserve the sending failure.");
            return;
        } catch (RuntimeException actual) {
            throw new AssertionError(
                "Expected InvoiceSendException after saving the failed attempt.",
                actual
            );
        }

        throw new AssertionError("Expected sending to fail.");
    }
}