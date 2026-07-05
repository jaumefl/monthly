package monthly.repository;

import java.util.Set;

public interface TransferRepository {
    void mark(String fingerprint);
    void unmark(String fingerprint);
    Set<String> findAll();
}