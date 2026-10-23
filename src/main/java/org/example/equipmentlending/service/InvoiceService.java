package org.example.equipmentlending.service;

import java.time.LocalDateTime;

import org.example.equipmentlending.model.Invoice;
import org.example.equipmentlending.notification.InvoiceSender;
import org.example.equipmentlending.repository.InvoiceRepository;

public class InvoiceService {
    private final InvoiceSender sender;
    private final InvoiceRepository repository;
    public InvoiceService(InvoiceSender sender, InvoiceRepository repository){
        if(sender == null){
            throw new IllegalArgumentException("Invoice sender must not be null.");
        }
        if(repository == null){
            throw new IllegalArgumentException("Invoice reposatory must not be null.");
        }
        this.sender = sender;
        this.repository = repository;
    }
    public void sendInvoice(Invoice invoice){
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null.");
        }

        invoice.recordSendAttempt(LocalDateTime.now());

        try {
            sender.send(invoice);
            invoice.markAsSent();
        } catch (RuntimeException sendFailure) {
            try {
                repository.save(invoice);
            } catch (RuntimeException saveFailure) {
                sendFailure.addSuppressed(saveFailure);
            }

            throw sendFailure;
        }
        repository.save(invoice);
    }
}
