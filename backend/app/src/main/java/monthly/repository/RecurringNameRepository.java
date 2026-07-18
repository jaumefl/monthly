package monthly.repository;

import java.util.Map;

public interface RecurringNameRepository {
    void set(String seriesKey, String name);
    void clear(String seriesKey);
    Map<String, String> findAll();
}