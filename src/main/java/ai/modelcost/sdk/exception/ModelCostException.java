package ai.modelcost.sdk.exception;

/**
 * Base exception for all ModelCost SDK errors.
 */
public class ModelCostException extends RuntimeException {

    public ModelCostException(String message) {
        super(message);
    }

    public ModelCostException(String message, Throwable cause) {
        super(message, cause);
    }
}
