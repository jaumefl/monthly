package monthly.repository;

import monthly.domain.Category;
import java.util.Map;

public interface CategoryOverrideRepository {
    void set(String fingerprint, Category category);
    void clear(String fingerprint);
    Map<String, Category> findAll();
}