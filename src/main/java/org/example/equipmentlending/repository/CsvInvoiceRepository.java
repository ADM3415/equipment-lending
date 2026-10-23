package org.example.equipmentlending.repository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;

import org.example.equipmentlending.model.Invoice;
import org.example.equipmentlending.model.StudentCharge;

public class CsvInvoiceRepository implements InvoiceRepository{
    private final Path filePath;
    private final ChargeRepository chargeRepository;
    private static final String HEADER ="id,chargeId,issuedDate,isSent,lastSendAttemptAt";
    public CsvInvoiceRepository(Path filePath, ChargeRepository chargeRepository){
        if(filePath == null){
            throw new IllegalArgumentException("File path must not be null.");
        }
        if(chargeRepository == null){
            throw new IllegalArgumentException("Charge reposatory must not be null.");
        }
        this.filePath = filePath;
        this.chargeRepository = chargeRepository;
    }
    @Override
    public Optional<Invoice> findById(int id){
        if(id<=0){
            throw new IllegalArgumentException("Invoice id must be positive.");
        }
        try{
            List<String> lines = Files.readAllLines(this.filePath,StandardCharsets.UTF_8);
            if(lines.isEmpty()){
                return Optional.empty();
            }
            if(!lines.get(0).equals(HEADER)){
                throw new IllegalStateException("Invalid invoice CSV header.");
            }
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()){
                    continue;
                }
                String[] fields = line.split(",", -1);
                if(fields.length != 5){
                    throw new IllegalStateException("Invalid invoice CSV row at line " + (i + 1));
                }
                int invoiceId = Integer.parseInt(fields[0]);
                if(invoiceId != id){
                    continue;
                }
                int chargeId = Integer.parseInt(fields[1]);
                LocalDate issuedDate = LocalDate.parse(fields[2]);
                if(!fields[3].equals("true") && !fields[3].equals("false")){
                    throw new IllegalStateException(
                        "Invalid sent status at line " + (i + 1)
                    );
                }
                boolean sent = Boolean.parseBoolean(fields[3]);
                LocalDateTime lastAttempt = fields[4].isBlank() ? null : LocalDateTime.parse(fields[4]);
                StudentCharge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalStateException("Invoice " +invoiceId+" references missing charge "+chargeId));
                Invoice invoice = new Invoice(invoiceId, charge, issuedDate, sent, lastAttempt);
                return Optional.of(invoice);
            }
            return Optional.empty();
        }
        catch (NoSuchFileException exception){
            return Optional.empty();
        }
        catch(IOException exception){
            throw new UncheckedIOException("Could not read invoices from "+this.filePath,exception);
        }
    }
    @Override 
    public void save(Invoice invoice){
        if(invoice == null){
            throw new IllegalArgumentException("Invoice must not be null.");
        }
        String row = invoice.getId() + ","
            + invoice.getCharge().getId() + ","
            + invoice.getIssuedDate() + ","
            + invoice.isSent() + ","
            + (invoice.getLastSendAttemptAt() == null ? "" : invoice.getLastSendAttemptAt().toString());
        boolean found = false;
        try{
            Path parent = filePath.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            List<String> lines;
            try{
                lines = new ArrayList<>(
                    Files.readAllLines(filePath, StandardCharsets.UTF_8)
                );
            }
            catch(NoSuchFileException exception){
                lines = new ArrayList<>();
            }
            if(lines.isEmpty()){
                lines.add(HEADER);
            } 
            else if(!lines.get(0).equals(HEADER)) {
                throw new IllegalStateException("Invalid invoice CSV header.");
            }
            for(int i = 1; i < lines.size(); i++){
                String line = lines.get(i);
                if(line.isBlank()){
                    continue;
                }
                String[] fields = line.split(",", -1);
                if(fields.length != 5){
                    throw new IllegalStateException(
                        "Invalid invoice CSV row at line " + (i + 1)
                    );
                }
                int existingId = Integer.parseInt(fields[0]);
                if (existingId == invoice.getId()) {
                    lines.set(i, row);
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add(row);
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8);
        }
        catch(IOException exception){
            throw new UncheckedIOException("Could not read invoices from "+this.filePath,exception);
        }
    }
}
