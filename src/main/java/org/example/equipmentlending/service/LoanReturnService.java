package org.example.equipmentlending.service;

import java.time.LocalDate;

import org.example.equipmentlending.model.ItemCondition;
import org.example.equipmentlending.model.ItemStatus;
import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.model.Item;
import org.example.equipmentlending.model.Member;
import org.example.equipmentlending.repository.ChargeRepository;
import org.example.equipmentlending.repository.ItemRepository;
import org.example.equipmentlending.repository.LoanRepository;


public class LoanReturnService {
    private final LoanRepository loanRepository;
    private final ItemRepository itemRepository;
    private final ChargeRepository chargeRepository;
    public LoanReturnService(LoanRepository loanRepository, ItemRepository itemRepository, ChargeRepository chargeRepository){
        if(loanRepository == null){
            throw new IllegalArgumentException("Loan repository can not be null.");
        }
        if(itemRepository == null){
            throw new IllegalArgumentException("Item repository can not be null.");
        }
        if(chargeRepository == null){
            throw new IllegalArgumentException("Charge repository can not be null.");
        }
        this.loanRepository = loanRepository;
        this.chargeRepository = chargeRepository;
        this.itemRepository = itemRepository;
    }
    public Loan returnLoan(int loanId, Member returnedBy, Staff processedBy, LocalDate returnDate, ItemCondition returnCondition, String description){
        if(loanId <= 0){
            throw new IllegalArgumentException("Loan id must be positive.");
        }
        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
        Item item = loan.getItem();
        if (item.getStatus() != ItemStatus.ON_LOAN) {
        throw new IllegalStateException( "Item is not on loan so it cannot be returned.");
        }
        loan.markAsReturned(returnedBy,processedBy,returnDate,returnCondition,description);
        item.markAsReturned(returnCondition);
        return loan;
        }

}
