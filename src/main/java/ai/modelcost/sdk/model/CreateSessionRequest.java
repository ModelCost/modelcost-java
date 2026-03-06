package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request payload for creating an agent session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateSessionRequest {

    @JsonProperty("api_key")
    private final String apiKey;

    @JsonProperty("session_id")
    private final String sessionId;

    @JsonProperty("feature")
    private final String feature;

    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("max_spend_usd")
    private final Double maxSpendUsd;

    @JsonProperty("max_iterations")
    private final Integer maxIterations;

    private CreateSessionRequest(Builder builder) {
        this.apiKey = builder.apiKey;
        this.sessionId = builder.sessionId;
        this.feature = builder.feature;
        this.userId = builder.userId;
        this.maxSpendUsd = builder.maxSpendUsd;
        this.maxIterations = builder.maxIterations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFeature() {
        return feature;
    }

    public String getUserId() {
        return userId;
    }

    public Double getMaxSpendUsd() {
        return maxSpendUsd;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public static class Builder {
        private String apiKey;
        private String sessionId;
        private String feature;
        private String userId;
        private Double maxSpendUsd;
        private Integer maxIterations;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder feature(String feature) {
            this.feature = feature;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder maxSpendUsd(Double maxSpendUsd) {
            this.maxSpendUsd = maxSpendUsd;
            return this;
        }

        public Builder maxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public CreateSessionRequest build() {
            Objects.requireNonNull(apiKey, "apiKey is required");
            Objects.requireNonNull(sessionId, "sessionId is required");
            return new CreateSessionRequest(this);
        }
    }
}
