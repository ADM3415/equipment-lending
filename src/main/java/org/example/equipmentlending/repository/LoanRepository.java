package org.example.equipmentlending.repository;

import java.util.List;
import java.util.Optional;
import org.example.equipmentlending.model.Loan;

public interface LoanRepository {
    List<Loan> findByStudentId(int studentId);
    void save(Loan loan);
    Optional<Loan> findById(int loandId);
    void insert(Loan loan);
}
