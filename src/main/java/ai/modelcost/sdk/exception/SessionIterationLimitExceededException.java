package ai.modelcost.sdk.exception;

/**
 * Thrown when a session would exceed its configured iteration limit.
 */
public class SessionIterationLimitExceededException extends ModelCostException {

    private final String sessionId;
    private final int currentIterations;
    private final int maxIterations;

    public SessionIterationLimitExceededException(String message, String sessionId, int currentIterations, int maxIterations) {
        super(message);
        this.sessionId = sessionId;
        this.currentIterations = currentIterations;
        this.maxIterations = maxIterations;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getCurrentIterations() {
        return currentIterations;
    }

    public int getMaxIterations() {
        return maxIterations;
    }
}
