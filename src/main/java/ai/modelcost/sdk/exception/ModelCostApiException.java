package ai.modelcost.sdk.exception;

/**
 * Thrown when the ModelCost API returns an error response.
 */
public class ModelCostApiException extends ModelCostException {

    private final int statusCode;
    private final String errorCode;

    public ModelCostApiException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ModelCostApiException(String message, int statusCode, String errorCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
