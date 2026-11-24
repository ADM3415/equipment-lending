package org.example.equipmentlending.service;

import java.time.LocalDate;

import java.sql.Connection;
import java.sql.SQLException;

import org.example.equipmentlending.model.MemberType;
import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.database.ConnectionFactory;
import org.example.equipmentlending.model.ItemCondition;


public class JdbcLoanReturnService {
    private final ConnectionFactory connectionFactory;

    public JdbcLoanReturnService(ConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("Connection factory must not be null.");
        }
        this.connectionFactory = connectionFactory;
    }
    public Loan returnLoan(int loanId, MemberType returnedByType, int returnedById, int processedById, LocalDate returnDate, ItemCondition returnCondition, String description){
        if(loanId <= 0){
            throw new IllegalArgumentException("Loan id must be positive.");
        }
        if(returnedByType == null){
            throw new IllegalArgumentException("Must provide the member's type.");
        }
        if(returnedById <= 0){
            throw new IllegalArgumentException("Reterner's  id must be positive");
        }
        if(processedById <= 0){
            throw new IllegalArgumentException("Processor's id must be positive");
        }
        if(returnDate == null){
            throw new IllegalArgumentException("Must provide the return date.");
        }
        if(returnCondition == null){
            throw new IllegalArgumentException("Must provide return condition.");
        }
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
