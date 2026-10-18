# Working requirements

These notes summarize the course discussion, not a final client sign-off.

## Loans and access

- Staff record loans and returns and consult loan history and item location.
- Teacher access and its precise permissions need confirmation against the use-case diagram; students do not directly operate the initial staff-facing workflow.
- Only active students may borrow. A student may have at most two active loans; an item may have at most one active loan.
- The working loan period is seven calendar days, without renewals in the first version.
- Overdue loans or unpaid charges block new borrowing.
- Damaged or lost equipment cannot be loaned.
- Track the student, item, registering staff, borrowing date, due date, and closure information.
- Another staff member may process a return. Track this staff member separately from the actual person returning the item.
- The borrower or a responsible staff member/teacher may physically return the item; another student may not.

## Returns, loss, charges, and invoices

- Keep return condition and description as historical evidence.
- Loss is reported separately from a physical return; retain the missing item's record.
- Working late-fee rate: 5 MAD per calendar day after the due date, never negative.
- Use lossReportDate when present; otherwise use returnDate for closed loans. Active estimates use the calculation date.
- Stop late-fee accumulation on closure. Damage/loss amounts require an approved assessment; do not invent a pricing formula.
- Charges have a type, amount, description, associated loan, and payment status.
- Payment happens outside the app. Record a full settlement, date, receipt reference, and processing staff; reject duplicate settlement.
- An invoice requires a charge. A charge may exist before its invoice is generated.
- Sending failure must permit retry and preserve recorded loans, charges, and invoices.
- A failed loan save must report an error to current staff, never false success.

## Still to specify

Exact invoice schedule, delivery confirmation semantics, maximum charges per loan, permissions, damage approval process, persistence transactions, and concurrency handling. The initial maximum-three-charges idea is not yet a validated invariant.
