package org.example.equipmentlending.repository;

import java.util.Optional;

import org.example.equipmentlending.model.Teacher;

public interface TeacherRepository{
    Optional<Teacher> findById(int id);
    void save(Teacher teacher);
}
