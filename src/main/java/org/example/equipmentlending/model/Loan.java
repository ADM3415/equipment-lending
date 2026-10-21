package org.example.equipmentlending.model;

import java.time.LocalDate;

public class Loan {
    private final int id;
    private Student student;
    private Item item;
    private Staff registeredBy;
    private LocalDate borrowDate;
    private LocalDate dueDate;

    private Staff returnProcessedBy;
    private Member returnedBy;
    private LocalDate returnDate;
    private LocalDate lossReportedDate;
    private LoanStatus status;
    private ItemCondition returnCondition;
    private String returnDescription;
    
    public Loan(int id, Student student, Item item,
            Staff registeredBy, LocalDate borrowDate){
                if (id <= 0) {
                    throw new IllegalArgumentException(
                        "Loan ID must be positive."
                    );
                }
                if (student == null) {
                    throw new IllegalArgumentException(
                        "Student must not be null."
                    );
                }
                if (item == null) {
                    throw new IllegalArgumentException(
                        "Item must not be null."
                    );
                }
                if (registeredBy == null) {
                    throw new IllegalArgumentException(
                        "Registering staff must not be null."
                    );
                }
                if (borrowDate == null) {
                    throw new IllegalArgumentException(
                        "Borrowing date must not be null."
                    );
                }

                this.id = id;
                this.student = student;
                this.item = item;
                this.registeredBy = registeredBy;
                this.borrowDate = borrowDate;
                this.dueDate = this.borrowDate.plusDays(7);

                this.returnProcessedBy = null;
                this.returnedBy = null;
                this.returnDate = null;
                this.status = LoanStatus.ACTIVE;
                this.returnDescription = null;
                this.lossReportedDate = null;
                this.returnCondition = null;
            }
            public int getId() {
                return this.id;
            }
            public Student getStudent() {
                return this.student;
            }
            public Item getItem() {
                return this.item;
            }
            public Staff getRegisteredBy() {
                return this.registeredBy;
            }
            public LocalDate getBorrowDate() {
                return this.borrowDate;
            }
            public LocalDate getDueDate() {
                return this.dueDate;
            }
            public Staff getReturnProcessedBy() {
                return this.returnProcessedBy;
            }
            public Member getReturnedBy() {
                return this.returnedBy;
            }
            public LocalDate getReturnDate() {
                return this.returnDate;
            }
            public LocalDate getLossReportedDate() {
                return this.lossReportedDate;
            }
            public LoanStatus getStatus() {
                return this.status;
            }
            public ItemCondition getReturnCondition() {
                return this.returnCondition;
            }
            public String getReturnDescription() {
                return this.returnDescription;
            }
            public boolean isOverdue(LocalDate onDate){
                if(onDate == null){
                    throw new IllegalArgumentException("Date must not be null.");
                }
                if(this.status != LoanStatus.ACTIVE){
                    return false;
                }
                return onDate.isAfter(this.dueDate);
            }
            public void markAsReturned( Member returnedBy,Staff processedBy,LocalDate returnDate,ItemCondition condition,String description){
                if(this.status != LoanStatus.ACTIVE){
                    throw new IllegalStateException("Loan is not active so it can not be returned.");
                }
                if(returnedBy == null){
                    throw new IllegalArgumentException("Loan must be returned by someone.");
                }
                if(processedBy == null){
                    throw new IllegalArgumentException("Loan must be proccesed by someone.");
                }
                if(returnDate == null){
                    throw new IllegalArgumentException("Loan must have a return date.");
                }
                if(condition == null){
                    throw new IllegalArgumentException("Loan must have a return condition.");
                }
                if (condition != ItemCondition.GOOD && (description == null || description.isBlank())) {
                    throw new IllegalArgumentException("Please describe the damage.");
                }
                this.returnedBy = returnedBy;
                this.returnProcessedBy = processedBy;
                this.returnDate = returnDate;
                this.returnCondition = condition;
                this.returnDescription = description;
                this.status = LoanStatus.RETURNED;
            }
}