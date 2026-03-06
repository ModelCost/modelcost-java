package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the budget check endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetCheckResponse {

    private boolean allowed;
    private String action;

    @JsonProperty("throttle_percentage")
    private Integer throttlePercentage;

    private String reason;

    public BudgetCheckResponse() {
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getAction() {
        return action;
    }

    public Integer getThrottlePercentage() {
        return throttlePercentage;
    }

    public String getReason() {
        return reason;
    }
}
