package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.example.equipmentlending.model.Item;
import org.example.equipmentlending.model.ItemType;
import org.example.equipmentlending.model.ItemCondition;
import org.example.equipmentlending.model.ItemStatus;

public class JdbcItemRepository implements ItemRepository {
    private final Connection connection;

    public JdbcItemRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection must not be null.");
        }
        this.connection = connection;
    }

    @Override
    public Optional<Item> findById(int id) {
        if(id <= 0){
            throw new IllegalArgumentException("Id must be positive.");
        }
        String sql = "SELECT id, name, type, item_condition, storage_location, status FROM items WHERE id = ?";
        try(PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setInt(1, id);
            try(ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    return Optional.empty();
                }
                int itemId = result.getInt("id");
                String name = result.getString("name");
                String location = result.getString("storage_location");
                ItemType type = ItemType.valueOf(result.getString("type"));
                ItemCondition condition = ItemCondition.valueOf(result.getString("item_condition"));
                ItemStatus status = ItemStatus.valueOf(result.getString("status"));
                Item item = new Item(type, itemId, name, condition, location, status);
                return Optional.of(item);
            }
        } catch(SQLException exception){
            throw new IllegalStateException("Could not load item with ID "+id,exception);
        }
    }

    @Override
    public void save(Item item) {
        if(item == null){
            throw new IllegalArgumentException("Item must not be null.");
        }
        String sql = """
            INSERT INTO items (id, name, type, item_condition, storage_location, status)VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?,type = ?,item_condition = ?,storage_location = ?,status = ?""";
        try(PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setInt(1, item.getId());
            statement.setString(2, item.getName());
            statement.setString(3, item.getType().name());
            statement.setString(4, item.getCondition().name());
            statement.setString(5, item.getStorageLocation());
            statement.setString(6, item.getStatus().name());
            statement.setString(7, item.getName());
            statement.setString(8, item.getType().name());
            statement.setString(9, item.getCondition().name());
            statement.setString(10, item.getStorageLocation());
            statement.setString(11, item.getStatus().name());
            statement.executeUpdate();
        }
        catch (SQLException exception){
            throw new IllegalStateException(
                "Could not save item with ID " + item.getId(),exception
            );
        }
    }
}
