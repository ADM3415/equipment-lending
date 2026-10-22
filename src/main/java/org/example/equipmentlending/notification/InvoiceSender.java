package org.example.equipmentlending.notification;

import org.example.equipmentlending.model.Invoice;

public interface InvoiceSender {
    void send(Invoice invoice);
}