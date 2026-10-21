package org.example.equipmentlending.model;

public class Student implements Member {
    private String fullName;
    private boolean active;
    private String universityEmail;
    private final int id;
    public Student(String fullName,boolean active,String universityEmail,int id){
        if (fullName == null || fullName.isBlank()){
            throw new IllegalArgumentException("Student name must not be blank.");
        }
        if (universityEmail == null || universityEmail.isBlank()){
            throw new IllegalArgumentException("University email must not be blank.");
        }
        if (id<=0){
            throw new IllegalArgumentException("Id must be a positif integer.");
        }
        this.fullName = fullName;
        this.active = active;
        this.universityEmail = universityEmail;
        this.id = id;
    }
    @Override
    public MemberType getType() {
        return MemberType.STUDENT;
    }
    @Override
    public int getId(){
        return this.id;
    }
    @Override
    public String  getName(){
        return this.fullName;
    }
    public String getUniversityEmail(){
        return this.universityEmail;
    }
    public boolean isActive(){
        return this.active;
    }

}
