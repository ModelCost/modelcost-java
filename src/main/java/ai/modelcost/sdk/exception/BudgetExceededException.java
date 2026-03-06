package ai.modelcost.sdk.exception;

/**
 * Thrown when an operation would exceed the configured budget limits.
 */
public class BudgetExceededException extends ModelCostException {

    private final double remainingBudget;
    private final String scope;
    private final String overrideUrl;

    public BudgetExceededException(String message, double remainingBudget, String scope, String overrideUrl) {
        super(message);
        this.remainingBudget = remainingBudget;
        this.scope = scope;
        this.overrideUrl = overrideUrl;
    }

    public double getRemainingBudget() {
        return remainingBudget;
    }

    public String getScope() {
        return scope;
    }

    public String getOverrideUrl() {
        return overrideUrl;
    }
}
