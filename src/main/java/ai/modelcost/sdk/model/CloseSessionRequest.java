package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request payload for closing an agent session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CloseSessionRequest {

    @JsonProperty("api_key")
    private final String apiKey;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("termination_reason")
    private final String terminationReason;

    @JsonProperty("final_spend_usd")
    private final Double finalSpendUsd;

    @JsonProperty("final_iteration_count")
    private final Integer finalIterationCount;

    private CloseSessionRequest(Builder builder) {
        this.apiKey = builder.apiKey;
        this.status = builder.status;
        this.terminationReason = builder.terminationReason;
        this.finalSpendUsd = builder.finalSpendUsd;
        this.finalIterationCount = builder.finalIterationCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getStatus() {
        return status;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public Double getFinalSpendUsd() {
        return finalSpendUsd;
    }

    public Integer getFinalIterationCount() {
        return finalIterationCount;
    }

    public static class Builder {
        private String apiKey;
        private String status;
        private String terminationReason;
        private Double finalSpendUsd;
        private Integer finalIterationCount;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder terminationReason(String terminationReason) {
            this.terminationReason = terminationReason;
            return this;
        }

        public Builder finalSpendUsd(Double finalSpendUsd) {
            this.finalSpendUsd = finalSpendUsd;
            return this;
        }

        public Builder finalIterationCount(Integer finalIterationCount) {
            this.finalIterationCount = finalIterationCount;
            return this;
        }

        public CloseSessionRequest build() {
            Objects.requireNonNull(apiKey, "apiKey is required");
            return new CloseSessionRequest(this);
        }
    }
}
