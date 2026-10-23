package org.example.equipmentlending.service;

import java.time.LocalDate;
import java.util.List;

import org.example.equipmentlending.model.Item;
import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.model.LoanStatus;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.model.Student;
import org.example.equipmentlending.model.StudentCharge;
import org.example.equipmentlending.repository.ChargeRepository;
import org.example.equipmentlending.repository.LoanRepository;
import org.example.equipmentlending.repository.ItemRepository;

public class LoanRegistrationService {
    private final LoanRepository loanRepository;
    private final ChargeRepository chargeRepository;
    private final ItemRepository itemRepository;
    public LoanRegistrationService(LoanRepository loanRepository, ChargeRepository chargeRepository, ItemRepository itemRepository){
        if(loanRepository == null){
            throw new IllegalArgumentException("Loan repository must not be null.");
        }
        if(chargeRepository == null){
            throw new IllegalArgumentException("Charge repository must not be null.");
        }
        if(itemRepository == null){
            throw new IllegalArgumentException("Item repository must not be null.");
        }
        this.loanRepository = loanRepository;
        this.chargeRepository = chargeRepository;
        this.itemRepository = itemRepository;
    }
    public Loan registerLoan(int id,Student student,Item item, Staff registeredBy, LocalDate borrowDate){
        if(item == null){
            throw new IllegalArgumentException("Item must not be null.");
        }
        Item storedItem = itemRepository.findById(item.getId()).orElseThrow(()-> new IllegalArgumentException("Item not found: "+item.getId()));
        Loan createdLoan = new Loan(id, student, storedItem, registeredBy, borrowDate);
        if(!storedItem.isAvailable()){
            throw new IllegalStateException("Item not available.");
        }
        if(!student.isActive()){
            throw new IllegalStateException("Student is not active.");
        }
        List<Loan> loans = loanRepository.findByStudentId(student.getId());
        int activeCount = 0;
        for (Loan loan : loans) {
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                activeCount++;
            }
            if(loan.isOverdue(borrowDate)){
                throw new IllegalStateException("Student has an overdue loan.");
            }
        }
        if (activeCount >= 2) {
            throw new IllegalStateException(
                "Student already has two active loans."
            );
        }
        List<StudentCharge> charges = chargeRepository.findByStudentId(student.getId());
        for(StudentCharge charge : charges){
            if (!charge.isPaid()) {
                throw new IllegalStateException(
                    "Student has an unpaid charge."
                );
            }
        }
        storedItem.markAsLoaned();
        loanRepository.save(createdLoan);
        itemRepository.save(storedItem);
        return createdLoan;
    }
}
