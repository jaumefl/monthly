package monthly.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public record Transaction(
        LocalDate operationDate,
        String description,
        BigDecimal amount,      // negative = expense, positive = income
        String currency,        // "EUR"
        BankSource source
) {
    public YearMonth month() {
        return YearMonth.from(operationDate);
    }

    /**
     * Stable identity derived from the immutable source fields, so a transaction
     * keeps the same fingerprint across re-imports (survives delete + re-insert).
     * Description is normalized (case + whitespace) so trivial formatting drift
     * doesn't break the match.
     */
    public String fingerprint() {
        String normalizedDesc = description == null ? ""
                : description.trim().toLowerCase().replaceAll("\\s+", " ");
        String raw = String.join("|",
                source.name(),
                operationDate.toString(),
                amount.toPlainString(),
                normalizedDesc);
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}