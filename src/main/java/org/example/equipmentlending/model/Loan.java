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
    
    public Loan(int id, Student student, Item item, Staff registeredBy, LocalDate borrowDate){
                if (id <= 0){
                    throw new IllegalArgumentException(
                        "Loan ID must be positive."
                    );
                }
                if (student == null){
                    throw new IllegalArgumentException(
                        "Student must not be null."
                    );
                }
                if (item == null){
                    throw new IllegalArgumentException(
                        "Item must not be null."
                    );
                }
                if (registeredBy == null){
                    throw new IllegalArgumentException(
                        "Registering staff must not be null."
                    );
                }
                if (borrowDate == null){
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
            public int getId(){
                return this.id;
            }
            public Student getStudent(){
                return this.student;
            }
            public Item getItem(){
                return this.item;
            }
            public Staff getRegisteredBy(){
                return this.registeredBy;
            }
            public LocalDate getBorrowDate(){
                return this.borrowDate;
            }
            public LocalDate getDueDate(){
                return this.dueDate;
            }
            public Staff getReturnProcessedBy(){
                return this.returnProcessedBy;
            }
            public Member getReturnedBy(){
                return this.returnedBy;
            }
            public LocalDate getReturnDate(){
                return this.returnDate;
            }
            public LocalDate getLossReportedDate(){
                return this.lossReportedDate;
            }
            public LoanStatus getStatus(){
                return this.status;
            }
            public ItemCondition getReturnCondition(){
                return this.returnCondition;
            }
            public String getReturnDescription(){
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
                if (condition != ItemCondition.GOOD && (description == null || description.isBlank())){
                    throw new IllegalArgumentException("Please describe the damage.");
                }
                this.returnedBy = returnedBy;
                this.returnProcessedBy = processedBy;
                this.returnDate = returnDate;
                this.returnCondition = condition;
                this.returnDescription = description;
                this.status = LoanStatus.RETURNED;
            }
            public void markAsLost(Staff processedBy, String description, LocalDate lossReportedDate){

                if (this.status != LoanStatus.ACTIVE){
                    throw new IllegalStateException(
                        "Loan is not active so it cannot be reported as lost."
                    );
                }
                if (processedBy == null){
                    throw new IllegalArgumentException(
                        "Processing staff must not be null."
                    );
                }
                if (lossReportedDate == null){
                    throw new IllegalArgumentException(
                        "Loss report date must not be null."
                    );
                }
                if (lossReportedDate.isBefore(this.borrowDate)){
                    throw new IllegalArgumentException(
                        "Loss report date cannot be before borrowing date."
                    );
                }
                if (description == null || description.isBlank()){
                    throw new IllegalArgumentException("Please describe the loss.");
                }

                this.lossReportedDate = lossReportedDate;
                this.returnProcessedBy = processedBy;
                this.returnDescription = description;
                this.status = LoanStatus.CLOSED_LOST;
            }
            public static Loan restore(int id,Student student, Item item, Staff registeredBy, LocalDate borrowDate,
            LocalDate dueDate, LoanStatus status, Member returnedBy, Staff returnProcessedBy, LocalDate returnDate,
            LocalDate lossReportedDate, ItemCondition returnCondition, String returnDescription){
                Loan loan = new Loan(id, student, item, registeredBy, borrowDate);
                if (dueDate == null || dueDate.isBefore(borrowDate)){
                    throw new IllegalArgumentException(
                        "Due date must be on or after the borrow date."
                    );
                }
                if (status == null){
                    throw new IllegalArgumentException("Loan status must not be null.");
                }

                switch (status){
                    case ACTIVE -> {
                        if (returnedBy != null || returnProcessedBy != null
                                || returnDate != null || lossReportedDate != null
                                || returnCondition != null || returnDescription != null){
                            throw new IllegalArgumentException(
                                "An active loan cannot have return or loss details."
                            );
                        }
                    }

                    case RETURNED -> {
                        if (returnedBy == null || returnProcessedBy == null
                                || returnDate == null || returnCondition == null){
                            throw new IllegalArgumentException(
                                "A returned loan must record who returned it, who processed "
                                + "the return, the return date, and the item condition."
                            );
                        }
                        if (lossReportedDate != null){
                            throw new IllegalArgumentException(
                                "A returned loan cannot have a loss report date."
                            );
                        }
                        if (returnDate.isBefore(borrowDate)){
                            throw new IllegalArgumentException(
                                "Return date cannot be before the borrow date."
                            );
                        }
                        if (returnedBy.getType() == MemberType.STUDENT
                                && returnedBy.getId() != student.getId()){
                            throw new IllegalArgumentException(
                                "Only the borrowing student may return this loan as a student."
                            );
                        }
                        if (returnCondition != ItemCondition.GOOD
                                && (returnDescription == null || returnDescription.isBlank())){
                            throw new IllegalArgumentException("Please describe the damage.");
                        }
                    }

                    case CLOSED_LOST -> {
                        if (returnProcessedBy == null || lossReportedDate == null){
                            throw new IllegalArgumentException(
                                "A lost loan must record who processed the loss and when."
                            );
                        }
                        if (returnedBy != null || returnDate != null || returnCondition != null){
                            throw new IllegalArgumentException(
                                "A lost item cannot have a returner, return date, or return condition."
                            );
                        }
                        if (lossReportedDate.isBefore(borrowDate)){
                            throw new IllegalArgumentException(
                                "Loss report date cannot be before the borrow date."
                            );
                        }
                    }
                }

                loan.dueDate = dueDate;
                loan.status = status;
                loan.returnedBy = returnedBy;
                loan.returnProcessedBy = returnProcessedBy;
                loan.returnDate = returnDate;
                loan.lossReportedDate = lossReportedDate;
                loan.returnCondition = returnCondition;
                loan.returnDescription = returnDescription;

                return loan;
            }
}