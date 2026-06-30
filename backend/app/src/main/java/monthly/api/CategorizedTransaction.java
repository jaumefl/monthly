package monthly.api;

import monthly.domain.BankSource;
import monthly.domain.Category;
import monthly.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

// API response shape: a Transaction plus its computed Category.
// Lives in `api`, not `domain`, so the domain model stays category-free.
public record CategorizedTransaction(
        LocalDate operationDate,
        String description,
        BigDecimal amount,
        String currency,
        BankSource source,
        Category category
) {
    public static CategorizedTransaction of(Transaction tx, Category category) {
        return new CategorizedTransaction(
                tx.operationDate(),
                tx.description(),
                tx.amount(),
                tx.currency(),
                tx.source(),
                category
        );
    }
}