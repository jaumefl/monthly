package monthly.repository;

import monthly.domain.BankSource;
import monthly.domain.Transaction;

import java.time.YearMonth;
import java.util.List;

public interface TransactionRepository {

    void saveAll(List<Transaction> transactions);

    void deleteBySourceAndMonth(BankSource source, YearMonth month);

    List<Transaction> findByMonth(YearMonth month);
}