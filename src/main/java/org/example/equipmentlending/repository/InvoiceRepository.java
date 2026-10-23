package org.example.equipmentlending.repository;

import java.util.Optional;
import org.example.equipmentlending.model.Invoice;

public interface InvoiceRepository {
    void save(Invoice invoice);
    Optional<Invoice> findById(int id);
}
