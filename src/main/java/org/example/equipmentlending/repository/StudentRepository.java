package org.example.equipmentlending.repository;

import java.util.Optional;
import org.example.equipmentlending.model.Student;

public interface StudentRepository {
    Optional<Student> findById(int id);
    void save(Student student);
}