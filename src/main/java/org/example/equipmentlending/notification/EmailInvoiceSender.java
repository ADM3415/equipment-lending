package org.example.equipmentlending.notification;

import org.example.equipmentlending.model.Invoice;
import java.util.Properties;
import jakarta.mail.Session;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class EmailInvoiceSender implements InvoiceSender {
    private final String username;
    private final String password;
    public EmailInvoiceSender() {
        this.username = System.getenv("SMTP_USERNAME");
        this.password = System.getenv("SMTP_PASSWORD");

        if (username == null || username.isBlank()) {
            throw new IllegalStateException("SMTP_USERNAME is not configured.");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("SMTP_PASSWORD is not configured.");
        }
    }
    @Override
    public void send(Invoice invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice must not be null.");
        }

        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", "smtp.gmail.com");
        properties.setProperty("mail.smtp.port", "587");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");

        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            String recipient = invoice.getCharge()
                                    .getLoan()
                                    .getStudent()
                                    .getUniversityEmail();

            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(username));
            message.setRecipient(
                Message.RecipientType.TO,
                new InternetAddress(recipient, true)
            );

            message.setSubject(
                "Equipment lending — Invoice #" + invoice.getId(),
                "UTF-8"
            );

            message.setText(invoice.toString(), "UTF-8");

            Transport.send(message);

        } catch (MessagingException exception) {
            throw new IllegalStateException(
                "Could not send invoice #" + invoice.getId(),
                exception
            );
        }
            }

}