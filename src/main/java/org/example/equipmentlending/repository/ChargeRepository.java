package org.example.equipmentlending.repository;

import java.util.List;
import java.util.Optional;
import org.example.equipmentlending.model.StudentCharge;

public interface ChargeRepository {
    Optional<StudentCharge> findById(int chargeId);
    List<StudentCharge> findByStudentId(int studentId);
    void save(StudentCharge charge);
}