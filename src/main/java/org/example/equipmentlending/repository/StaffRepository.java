package org.example.equipmentlending.repository;

import java.util.Optional;
import org.example.equipmentlending.model.Staff;

public interface StaffRepository {
    Optional<Staff> findById(int id);
    void save(Staff staff);
}
