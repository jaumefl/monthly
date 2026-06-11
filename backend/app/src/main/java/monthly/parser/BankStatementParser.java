package monthly.parser;

import monthly.domain.*;
import java.io.InputStream;
import java.util.List;

public interface BankStatementParser {
    List<Transaction> parse(InputStream statement);
    BankSource source();
}