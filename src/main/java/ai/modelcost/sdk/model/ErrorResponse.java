package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Error response from the ModelCost API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {

    private String error;
    private String message;
    private int status;

    public ErrorResponse() {
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public int getStatus() {
        return status;
    }
}
