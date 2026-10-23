package org.example.equipmentlending.service;

import java.time.LocalDateTime;

import org.example.equipmentlending.model.Invoice;
import org.example.equipmentlending.notification.InvoiceSender;

public class InvoiceService {
    private final InvoiceSender sender;
    public InvoiceService(InvoiceSender sender){
        if(sender == null){
            throw new IllegalArgumentException("Invoice sender must not be null.");
        }
        this.sender = sender;
    }
    public void sendInvoice(Invoice invoice){
        if(invoice == null){
            throw new IllegalArgumentException("Invoice cannot be null.");
        }
        invoice.recordSendAttempt(LocalDateTime.now());
        this.sender.send(invoice);
        invoice.markAsSent();
    }
}
