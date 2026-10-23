package org.example.equipmentlending.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Invoice {
    private final int id;
    private final StudentCharge charge;
    private final LocalDate issuedDate;
    private boolean isSent;
    private LocalDateTime lastSendAttemptAt;
    public Invoice(int id, StudentCharge charge, LocalDate issueDate, boolean isSent, LocalDateTime lastSendAttemptAt){
        if(id<=0){
            throw new IllegalArgumentException("Id must be positive.");
        }
        if(charge == null){
            throw new IllegalArgumentException("Charge must exist to send invoice.");
        }
        if(issueDate == null){
            throw new IllegalArgumentException("Invoice must have an issued date.");
        }
        this.id = id;
        this.charge = charge;
        this.issuedDate = issueDate;
        this.isSent = isSent;
        this.lastSendAttemptAt = lastSendAttemptAt;
    }
    public Invoice(int id, StudentCharge charge, LocalDate issueDate){
        this(id, charge, issueDate, false, null);
    }
    public int getId(){
        return this.id;
    }
    public StudentCharge getCharge(){
        return this.charge;
    }
    public LocalDate getIssuedDate(){
        return this.issuedDate;
    }
    public boolean isSent(){
        return this.isSent;
    }
    public LocalDateTime getLastSendAttemptAt(){
        return this.lastSendAttemptAt;
    }
    public void recordSendAttempt(LocalDateTime attemptedAt){
        if(attemptedAt == null){
            throw new IllegalArgumentException("Must provide a time and date attempt.");
        }
        this.lastSendAttemptAt = attemptedAt;
    }
    public void markAsSent(){
        this.isSent = true;
    }
    @Override 
    public String toString(){
        return "Invoice #" + id
        + "\nIssued date: " + issuedDate
        + "\nStudent: " + charge.getLoan().getStudent().getName()
        + "\nStudent ID: " + charge.getLoan().getStudent().getId()
        + "\nCharge type: " + charge.getChargeType()
        + "\nDescription: " + charge.getDescription()
        + "\nAmount: " + charge.getAmount().toPlainString() + " MAD"
        + "\nTotal paid: " + charge.getTotalPaid().toPlainString() + " MAD"
        + "\nRemaining balance: " + charge.getRemainingAmount().toPlainString() + " MAD";
    }
}