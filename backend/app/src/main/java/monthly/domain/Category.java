package monthly.domain;

public enum Category {
    GROCERIES,
    EATING_OUT,
    TRANSPORT,
    HOUSING,
    UTILITIES,
    SHOPPING,
    HEALTH,
    INVESTMENT,
    SUBSCRIPTION,
    INCOME,
    OTHER;

    public boolean isAssignable() {
        return this != INCOME;
    }
}