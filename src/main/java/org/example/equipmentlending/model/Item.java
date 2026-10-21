package org.example.equipmentlending.model;

public class Item {
    private ItemType type;
    private final int id;
    private String name;
    private ItemCondition condition;
    private String storageLocation;
    private ItemStatus status; 
    public Item(ItemType type, int id, String name,
                ItemCondition condition, String storageLocation, 
                ItemStatus status){
                    if (id<=0){
                        throw new IllegalArgumentException("Item id can not be less than 0.");
                    }
                    if (name == null || name.isBlank()){
                        throw new IllegalArgumentException("Item name can not be empty.");
                    }
                    if (storageLocation == null || storageLocation.isBlank()){
                        throw new IllegalArgumentException("Item must have a storage loaction.");
                    }
                    if (type == null) {
                        throw new IllegalArgumentException("Item type must not be null.");
                    }
                    if (condition == null) {
                        throw new IllegalArgumentException("Item condition must not be null.");
                    }
                    if (status == null) {
                        throw new IllegalArgumentException("Item status must not be null.");
                    }
                    this.type = type;
                    this.id = id;
                    this.name = name; 
                    this.condition = condition;
                    this.storageLocation = storageLocation;
                    this.status = status;
    }
    public int getId(){
        return this.id;
    }
    public String getName(){
        return this.name;
    }
    public ItemType getType(){
        return this.type;
    }
    public String getStorageLocation(){
        return this.storageLocation;
    }
    public ItemCondition getCondition(){
        return this.condition;
    }
    public ItemStatus getStatus(){
        return this.status;
    }
    public boolean isAvailable(){
        return getCondition() == ItemCondition.GOOD && getStatus() == ItemStatus.IN_STOCK; 
    }
    public void markAsLoaned(){
        if(!isAvailable()){
            throw new IllegalStateException("Item is not available for borrowing.");
        }
        this.status = ItemStatus.ON_LOAN;
    }
    public void markAsReturned(ItemCondition  condition){
        if(condition == null){
            throw new IllegalArgumentException("Please specify the condition of the returned item.");
        }
        if(getStatus() != ItemStatus.ON_LOAN){
            throw new IllegalStateException("Item is not on loan so it cant be returned.");
        }
        this.condition = condition;
        this.status = ItemStatus.IN_STOCK;
    }
    public void markAsLost(){
        if(getStatus() != ItemStatus.ON_LOAN){
            throw new IllegalStateException("Item is not on loan so it cant be marked as lost.");
        }
        this.status = ItemStatus.LOST;
    }
}


