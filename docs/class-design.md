# Planned class design

The draw.io file is the current visual working design. This inventory is a guide for implementation, not generated code.

| Package | Type | Responsibility |
| --- | --- | --- |
| model | Member (interface) | Expose member ID, name, and type |
| model | Student | Student identity, university email, active enrollment |
| model | Staff | Staff identity |
| model | Teacher | Teacher identity |
| model | Item | Equipment identity, type, location, condition, status |
| model | Loan | Borrower, item, dates, staff attribution, return history |
| model | StudentCharge | Charge details and external-payment recording |
| model | Invoice | Invoice identity, charge, and sending metadata |
| service | LoanRegistrationService | Check eligibility and register a loan |
| service | ReturnService | Process returns/loss reports and calculate late fees |
| repository | LoanRepository (interface) | Find student loans and save loans |
| repository | ChargeRepository (interface) | Find student charges and save charges |
| notification | InvoiceSender (interface) | Sending contract |
| notification | EmailInvoiceSender | Email implementation of sending contract |

## Enums

- MemberType: STUDENT, STAFF, TEACHER
- ItemType: LAPTOP, CAMERA, PROJECTOR
- ItemCondition: GOOD, PHYSICAL_DAMAGE, SOFTWARE_ISSUE, UNSPECIFIED_DAMAGE
- ItemStatus: IN_STOCK, ON_LOAN, LOST
- LoanStatus: ACTIVE, RETURNED, CLOSED_LOST
- ChargeType: LATE_FEE, DAMAGE, LOSS

## Relationship notes

One student/item can have many historical loans. Active-loan limits are constraints, not historical association multiplicities. Each loan has one registering staff member; processing staff and returnedBy are optional until applicable. A charge belongs to one loan, and an invoice belongs to one charge. A service's persistent collaborator is an association; a parameter-only use can be a dependency. Interface implementations use realization arrows.

Avoid duplicating class-typed fields when the corresponding association already represents them. Use BigDecimal for Java money values and LocalDate for calendar dates. Finalize method signatures and repository coverage before coding.
