package ai.modelcost.sdk.exception;

/**
 * Thrown when a session would exceed its configured budget limit.
 */
public class SessionBudgetExceededException extends ModelCostException {

    private final String sessionId;
    private final double currentSpend;
    private final double maxSpend;

    public SessionBudgetExceededException(String message, String sessionId, double currentSpend, double maxSpend) {
        super(message);
        this.sessionId = sessionId;
        this.currentSpend = currentSpend;
        this.maxSpend = maxSpend;
    }

    public String getSessionId() {
        return sessionId;
    }

    public double getCurrentSpend() {
        return currentSpend;
    }

    public double getMaxSpend() {
        return maxSpend;
    }
}
