package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the create session endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateSessionResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("max_spend_usd")
    private Double maxSpendUsd;

    @JsonProperty("max_iterations")
    private Integer maxIterations;

    public CreateSessionResponse() {
    }

    public CreateSessionResponse(String id, String sessionId, String status) {
        this.id = id;
        this.sessionId = sessionId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStatus() {
        return status;
    }

    public Double getMaxSpendUsd() {
        return maxSpendUsd;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }
}
