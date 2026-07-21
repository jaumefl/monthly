# Monthly

[![CI](https://github.com/jaumefl/monthly/actions/workflows/ci.yml/badge.svg)](https://github.com/jaumefl/monthly/actions/workflows/ci.yml)

A personal finance tracker that turns raw monthly bank exports into a clean, categorized view of where my money actually goes.

I built Monthly to solve a real problem of my own: I hold accounts across several banks, each of which exports statements in its own messy format, and none of them gives me a single monthly picture. Monthly parses those exports, normalizes them into one model, categorizes each transaction, and serves a small web dashboard where I can browse a month, compare it against another, and correct anything the auto-categorizer gets wrong.

It's a single-user app that runs locally, and I've been developing it test-first with small, deliberate commits.

## What it does

- **Imports statements from multiple banks.** Each bank gets its own parser behind a common interface, so Santander's `.xlsx` (Spanish formatted `DD/MM/YYYY` dates, comma decimals, junk metadata rows), Revolut's clean CSV, and imaginBank's CSV all end up as the same normalized `Transaction`.
- **Aggregates by calendar month.** Income, expenses and net for any month, with transactions listed newest-first across all banks.
- **Categorizes automatically.** A keyword-based categorizer maps each transaction to a category (Groceries, Eating Out, Transport, Housing, Utilities, Shopping, Health, Investment, Subscription, Income, Other) using word-boundary matching. Income is derived from the amount's sign, so it can never be mislabeled.
- **Lets me override categories.** When the auto-categorizer is wrong, I can reassign a transaction from the table. Overrides are keyed by a content fingerprint so they survive re-importing the same month.
- **Compares two months side by side.** A dedicated page renders paired vertical bars per category (baseline vs. current) with the euro delta and percent change, scaled per-category so one large category doesn't flatten the rest.
- **Handles transfers between my own accounts.** Transactions can be flagged as transfers so they're excluded from the spending graphs and don't show up as phantom income or expense.
- **Idempotent imports.** Re-importing a `(bank, month)` pair replaces exactly that bank's transactions for that month, so repeated imports never double-count.
- **Sets a monthly budget per category.** I can give any category a spending limit; the compare page shows a fill bar of actual spend against the limit, how much is left to reach it, and flags anything over budget in red.
- **Tells me when the categorizer needs tuning.** Rather than guessing which merchants the keyword map misses, the app mines my own manual corrections: any merchant I've moved out of Other in two or more separate months is surfaced as a candidate keyword rule. Corrections within a single month are ignored, so a holiday's worth of one-off merchants doesn't pollute the map.

## Tech stack

- **Java 21**
- **Spark Java** (`spark-core`) — serves the JSON API and the static frontend from a single process and port (no CORS to manage)
- **SQLite** (`sqlite-jdbc`) for persistence. A file database in normal use, in-memory for tests
- **Apache POI** for reading Excel exports
- **Jackson** (with the JSR-310 module) for JSON serialization
- **Spock / Groovy 4** on the JUnit Platform for testing
- **Gradle** (Kotlin DSL)
- Vanilla **HTML / CSS / JavaScript** frontend. No framework, no build step

## Architecture

The design keeps the domain model clean and pushes everything bank-specific, storage-specific or presentation-specific out to the edges.

```
Bank export ──▶ Parser ──▶ Transaction ──▶ Repository (SQLite)
                                              │
                                              ▼
                                     TransactionQueryService
                                (categorizer + overrides + transfers)
                                              │
                                     ┌────────┴────────┐
                                     ▼                 ▼
                                  JSON API   ◀──── vanilla JS frontend
```

- **Parser-per-bank.** Every parser implements `BankStatementParser` (`parse(InputStream)` + `source()`), so adding a new bank means writing one class and one Spock spec — nothing else changes.
- **Money is always `BigDecimal`.** Negative means expense, positive means income. No floating point anywhere near currency.
- **Categorization is computed on read, not stored.** The `Transaction` domain type stays category-free; a `CategorizedTransaction` DTO in the API layer carries the category out to the client. This keeps re-categorization a pure, testable function of a transaction.
- **Repository/service layering.** Repositories wrap SQLite; services compose them into the operations the API needs. The domain never touches the database.
- **One process, one port.** Spark serves the API and the static frontend together, so a clone and a single `./gradlew run` gives you the whole app.

### Domain model

- `Transaction` (record): operation date, description, amount, currency, source bank, plus a `month()` helper.
- `BankSource` (enum): `SANTANDER`, `REVOLUT`, `IMAGINBANK`.
- `Category` (enum): the eleven categories above; `isAssignable()` blocks manual assignment of `INCOME`.
- `MonthSummary`, `CategoryBreakdown`, `MonthComparison`, `BudgetReport`: small value types that keep aggregation logic in the domain and unit-testable.
## API

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/months/{YYYY-MM}` | Categorized transactions for a month |
| `GET` | `/api/months/{YYYY-MM}/summary` | Income / expenses / net for a month |
| `GET` | `/api/comparison?month=YYYY-MM&baseline=YYYY-MM` | Category comparison (baseline defaults to the previous month) |
| `GET` | `/api/categories` | Assignable categories |
| `POST` | `/api/imports/{bank}` | Import a raw statement (request body is the file) |
| `PUT` / `DELETE` | `/api/transactions/{fingerprint}/category` | Set or clear a category override |
| `PUT` / `DELETE` | `/api/transactions/{fingerprint}/transfer` | Flag or unflag a transaction as a transfer |
| `GET` | `/api/budgets` | Configured monthly limit per category |
| `GET` | `/api/budgets/report?month=YYYY-MM` | Spend vs. limit per budgeted category |
| `PUT` / `DELETE` | `/api/budgets/{category}` | Set or clear a category's budget |
| `GET` | `/api/recurring` | Detected recurring payment series across all history |
| `PUT` / `DELETE` | `/api/recurring/name` | Set or clear a custom name for a series |
| `GET` | `/api/categorization/suggestions` | Merchants repeatedly re-categorized out of Other, with the months involved |

## Running it

Requires a JDK 21+. From the `backend/` directory:

```bash
./gradlew run
```

Then open <http://localhost:4567>. The SQLite database is created automatically at `backend/data/monthly.db` on first run.

To import a statement, use the upload control in the UI, or POST the file directly:

```bash
curl.exe --data-binary "@santander_export.xlsx" http://localhost:4567/api/imports/santander
```

## Testing

Tests are written first, with Spock's `given / when / then` structure and `@Unroll` data tables for the parsers and categorizer. Run them with:

```bash
./gradlew test
```
Every push and pull request also runs the full test suite on GitHub Actions, so nothing merges to `main` without a green build.

Alongside the unit specs, an HTTP integration test boots the real server on an unused port, imports a fixture statement over the API, and asserts the month summary and transaction endpoints end to end.

Bank exports contain personal data, so **no real statement is ever committed.** The repository ignores `data/` and all `.xlsx` / `.xls` / `.csv` files by default; test fixtures are hand-anonymized — real structure, fake amounts and descriptions — and live under `src/test/resources/`.

## Project layout

```
monthly/
└── backend/
    ├── settings.gradle.kts
    └── app/
        ├── build.gradle.kts
        └── src/
            ├── main/java/monthly/
            │   ├── App.java              # Spark wiring: routes + dependency assembly
            │   ├── domain/              # Transaction, Category, summaries — no I/O
            │   ├── parser/              # one parser per bank
            │   ├── repository/         # SQLite repositories
            │   ├── service/            # import + query services
            │   ├── api/                # DTOs (CategorizedTransaction, MonthComparison…)
            │   └── db/                 # SQLite connection wrapper
            ├── main/resources/public/  # vanilla HTML/CSS/JS frontend
            └── test/groovy/monthly/    # Spock specs, mirroring the main packages
```

## Notes

This is an evolving side project I use with my own accounts. It's deliberately scoped to a single local user with no authentication in this version, and the keyword categorizer is tuned to my own spending, so it's expected to need ongoing adjustment, which is why the app now surfaces its own blind spots from my correction history instead of relying on me to notice them.