package monthly.repository;

import java.util.Set;

/**
 * Series the user has marked as *not* actually recurring — false positives
 * from the detector.
 */
public interface RecurringDismissalRepository {
    void dismiss(String seriesKey);
    void restore(String seriesKey);
    Set<String> findAll();
}