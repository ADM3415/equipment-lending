package org.example.equipmentlending.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StudentCharge {
    private Loan loan;
    private final int id;
    private ChargeType type;
    private BigDecimal amount;
    private String description;
    private boolean isPaid;
    private final List<Payment> payments = new ArrayList<>();
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
        this.isPaid = false;
    }
    public int getId(){
        return this.id;
    }
    public boolean isPaid(){
        return this.isPaid;
    }
    public BigDecimal getRemainingAmount() {
        return this.amount.subtract(getTotalPaid());
    }
    public List<Payment> getPayments() {
        return List.copyOf(this.payments);
    }
    public Loan getLoan(){
        return this.loan;
    }
    public ChargeType getChargeType(){
        return this.type;
    }
    public BigDecimal getAmount(){
        return this.amount;
    }
    public String getDescription(){
        return this.description;
    }
    public BigDecimal getTotalPaid(){
        BigDecimal total = BigDecimal.ZERO;
        for( Payment payment : payments){
            total = total.add(payment.getAmount());
        }
        return total;
    }
    public void addPayment(Payment payment) {
        if(payment == null){
            throw new IllegalArgumentException("Payment must not be null.");
        }
        BigDecimal newTotal = getTotalPaid().add(payment.getAmount());
        if(isPaid){
            throw new IllegalStateException("Charge is already fully paid.");
        }
        if(newTotal.compareTo(amount) > 0) {
            throw new IllegalArgumentException("Payment exceeds the remaining amount.");
        }
        if(payments.contains(payment)) {
            throw new IllegalArgumentException("Payment has already been recorded.");
        }
        this.payments.add(payment);
        this.isPaid = newTotal.compareTo(amount) == 0;
    }
}