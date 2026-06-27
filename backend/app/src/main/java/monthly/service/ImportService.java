package monthly.service;

import monthly.domain.Transaction;
import monthly.parser.BankStatementParser;
import monthly.repository.TransactionRepository;

import java.io.InputStream;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImportService {

    private final TransactionRepository repository;

    public ImportService(TransactionRepository repository) {
        this.repository = repository;
    }

    public void importStatement(BankStatementParser parser, InputStream statement) {
        List<Transaction> transactions = parser.parse(statement);

        Map<YearMonth, List<Transaction>> byMonth = transactions.stream()
                .collect(Collectors.groupingBy(tx -> YearMonth.from(tx.operationDate())));

        byMonth.forEach((month, group) -> {
            repository.deleteBySourceAndMonth(parser.source(), month);
            repository.saveAll(group);
        });
    }
}