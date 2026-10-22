package org.example.equipmentlending.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StudentCharge {
    private Loan loan;
    private Staff paymentRecordedBy;
    private final int id;
    private ChargeType type;
    private BigDecimal amount;
    private String description;
    private boolean isPaid;
    private BigDecimal amountPaid;
    private LocalDate paymentDate;
    private String receiptReference;
    public StudentCharge(int id, Loan loan, ChargeType type, BigDecimal amount, String description){
        if(id <= 0){
            throw new IllegalArgumentException("Id must be positive.");
        }
        if(loan == null){
            throw new IllegalArgumentException("Loan must exist.");
        }
        if(type == null){
            throw new IllegalArgumentException("Please precise the charge type.");
        }
        if(description == null || description.isBlank()){
            throw new IllegalArgumentException("Please fill the description slot.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Charge amount must be positive.");
        }
        this.id = id;
        this.loan = loan; 
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.amountPaid = BigDecimal.ZERO;
        this.paymentDate = null;
        this.receiptReference = null;
        this.isPaid = false;
    }
    public void recordPayment(BigDecimal amountPaid, LocalDate paymentDate,String receiptReference, Staff processedBy){
        
    }
}    