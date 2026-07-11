package monthly.repository;

import monthly.domain.Category;
import java.math.BigDecimal;
import java.util.Map;

public interface BudgetRepository {
    void set(Category category, BigDecimal limit);
    void clear(Category category);
    Map<Category, BigDecimal> findAll();
}