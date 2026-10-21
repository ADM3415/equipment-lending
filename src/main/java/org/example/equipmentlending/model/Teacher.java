package org.example.equipmentlending.model;

public class Teacher implements Member {
    private String fullName;
    private final int id;
    public Teacher(String fullName,int id){
        if (fullName == null || fullName.isBlank()){
            throw new IllegalArgumentException("Teacher name must not be blank.");
        }
        if (id<=0){
            throw new IllegalArgumentException("Id must be a positif integer.");
        }
        this.fullName = fullName;
        this.id = id;
    }
    @Override
    public MemberType getType() {
        return MemberType.TEACHER;
    }
    @Override
    public int getId(){
        return this.id;
    }
    @Override
    public String  getName(){
        return this.fullName;
    }
}

