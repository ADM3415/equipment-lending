# Equipment Lending

A software engineering learning project for recording student equipment loans, returns, charges, and invoices.

Status: requirements and UML design in progress; application implementation has not started.

## Project layout

- `docs/diagrams/Loan.drawio`: latest copied use-case and class diagrams; open with diagrams.net.
- `docs/requirements.md`: working business requirements.
- `docs/class-design.md`: planned classes and responsibilities.
- `docs/progress.md`: progress ledger and next steps.
- `docs/study-notes.md`: course concepts and design discussion summary.
- `src/main/java/org/example/equipmentlending/`: future Java implementation.
- `src/main/resources/`: future application resources.
- `src/test/java/org/example/equipmentlending/`: future tests.

## Development

Use JDK 25 and Maven. Open this folder in VS Code. The Maven compiler targets Java 25.

Run `mvn test` once your development environment is ready. This scaffold has no application classes or tests yet; a successful empty build does not verify lending behavior.

## Publish as a new repository

1. Extract the ZIP and open the inner `equipment-lending` folder in VS Code.
2. On GitHub, create a new empty repository named `equipment-lending`. Leave the README, license, and gitignore options unchecked because local files are included here.
3. In the terminal for this folder run:

```sh
git init -b main
git add .
git commit -m "Initialize equipment lending project and design notes"
```

4. Copy your new repository's HTTPS URL. Replace `YOUR_REPOSITORY_URL` below with that actual URL before running:

```sh
git remote add origin YOUR_REPOSITORY_URL
git push -u origin main
```

The starter does not create a GitHub repository or configure a remote automatically.

## Diagram workflow

Edit `docs/diagrams/Loan.drawio`, save it, and commit the change. This is a snapshot of the Drive diagram, not a live sync. Export PNG or PDF previews alongside it when useful for reviews.
