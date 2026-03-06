package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the governance scan endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GovernanceScanResponse {

    @JsonProperty("is_allowed")
    private boolean isAllowed;

    private String action;

    private List<DetectedViolation> violations;

    @JsonProperty("redacted_text")
    private String redactedText;

    public GovernanceScanResponse() {
    }

    public boolean isAllowed() {
        return isAllowed;
    }

    public String getAction() {
        return action;
    }

    public List<DetectedViolation> getViolations() {
        return violations;
    }

    public String getRedactedText() {
        return redactedText;
    }
}
