package org.example.equipmentlending.repository;

import java.util.List;
import org.example.equipmentlending.model.Loan;

public interface LoanRepository {
    List<Loan> findByStudentId(int studentId);

    void save(Loan loan);
}
