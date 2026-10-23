package org.example.equipmentlending.repository;

import java.util.Optional;
import org.example.equipmentlending.model.Item;

public interface ItemRepository {
    Optional<Item> findById(int id);
    void save(Item item);
}