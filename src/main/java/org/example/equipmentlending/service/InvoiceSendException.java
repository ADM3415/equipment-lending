package org.example.equipmentlending.service;

public class InvoiceSendException extends RuntimeException {
    public InvoiceSendException(String msg, Throwable exception){
        super(msg,exception);
    }
}
