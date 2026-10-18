# Course notes

This is a relevant discussion summary, not a verbatim chat export.

- SRP: give each class or function one coherent responsibility.
- DRY: avoid duplicating business rules.
- Encapsulation: control state changes; private fields alone do not prevent sensitive information leaking through output.
- Separate business logic from console or GUI display. Return structured outcomes rather than printing in authentication/domain logic.
- Retry transient failures differently from invalid credentials. Keep retry policy separate.
- Refactoring preserves observable behavior; adding lockout rules changes behavior.
- Test normal paths, invalid input, edge cases, and integration behavior.
- Use cases describe actor goals. Include and extend belong to use-case modeling; class diagrams use associations, dependencies, generalization, and realization.
- An interface declares a contract; implementations can perform the same operation differently.
- Historical relationship counts differ from active-loan constraints.
- Keep payment processing outside this app; record the result.
- Preserve historical return information even after an item's current condition changes.

Learning approach: discuss each design choice, let the learner propose a solution, then review it before implementation.
