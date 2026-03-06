package ai.modelcost.sdk.exception;

/**
 * Thrown when the client has been rate-limited by the ModelCost API.
 */
public class RateLimitedException extends ModelCostException {

    private final double retryAfterSeconds;
    private final String limitDimension;

    public RateLimitedException(String message, double retryAfterSeconds, String limitDimension) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.limitDimension = limitDimension;
    }

    public double getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getLimitDimension() {
        return limitDimension;
    }
}
