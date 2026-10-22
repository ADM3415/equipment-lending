package org.example.equipmentlending.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Payment {
    private final BigDecimal amount;
    private final LocalDate paymentDate;
    private final String receiptReference;
    private final Staff staff;
    public Payment(BigDecimal amount, LocalDate paymentDate, String receiptReference, Staff staff){
        if(amount == null || amount.signum() <= 0 ){
            throw new IllegalArgumentException("Amount paid must be positive.");
        }
        if(paymentDate == null){
            throw new IllegalArgumentException("Payment date must be provided.");
        }
        if(receiptReference == null || receiptReference.isBlank()){
            throw new IllegalArgumentException("Please provide the receipt reference.");
        }
        if(staff == null){
            throw new IllegalArgumentException("Staff must process the paiment.");
        }
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.receiptReference = receiptReference;
        this.staff = staff;
    }
    public BigDecimal getAmount(){
        return this.amount;
    }
    public LocalDate getPaymentDate(){
        return this.paymentDate;
    }
    public String getReceiptReference(){
        return this.receiptReference;
    }
    public Staff getProcessedBy(){
        return this.staff;
    }
}
