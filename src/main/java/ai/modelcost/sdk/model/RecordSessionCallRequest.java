package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request payload for recording a call within an agent session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordSessionCallRequest {

    @JsonProperty("api_key")
    private final String apiKey;

    @JsonProperty("call_sequence")
    private final int callSequence;

    @JsonProperty("call_type")
    private final String callType;

    @JsonProperty("tool_name")
    private final String toolName;

    @JsonProperty("input_tokens")
    private final int inputTokens;

    @JsonProperty("output_tokens")
    private final int outputTokens;

    @JsonProperty("cumulative_input_tokens")
    private final int cumulativeInputTokens;

    @JsonProperty("cost_usd")
    private final double costUsd;

    @JsonProperty("cumulative_cost_usd")
    private final double cumulativeCostUsd;

    @JsonProperty("pii_detected")
    private final boolean piiDetected;

    private RecordSessionCallRequest(Builder builder) {
        this.apiKey = builder.apiKey;
        this.callSequence = builder.callSequence;
        this.callType = builder.callType;
        this.toolName = builder.toolName;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.cumulativeInputTokens = builder.cumulativeInputTokens;
        this.costUsd = builder.costUsd;
        this.cumulativeCostUsd = builder.cumulativeCostUsd;
        this.piiDetected = builder.piiDetected;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getCallSequence() {
        return callSequence;
    }

    public String getCallType() {
        return callType;
    }

    public String getToolName() {
        return toolName;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getCumulativeInputTokens() {
        return cumulativeInputTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public double getCumulativeCostUsd() {
        return cumulativeCostUsd;
    }

    public boolean isPiiDetected() {
        return piiDetected;
    }

    public static class Builder {
        private String apiKey;
        private int callSequence;
        private String callType;
        private String toolName;
        private int inputTokens;
        private int outputTokens;
        private int cumulativeInputTokens;
        private double costUsd;
        private double cumulativeCostUsd;
        private boolean piiDetected;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder callSequence(int callSequence) {
            this.callSequence = callSequence;
            return this;
        }

        public Builder callType(String callType) {
            this.callType = callType;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder cumulativeInputTokens(int cumulativeInputTokens) {
            this.cumulativeInputTokens = cumulativeInputTokens;
            return this;
        }

        public Builder costUsd(double costUsd) {
            this.costUsd = costUsd;
            return this;
        }

        public Builder cumulativeCostUsd(double cumulativeCostUsd) {
            this.cumulativeCostUsd = cumulativeCostUsd;
            return this;
        }

        public Builder piiDetected(boolean piiDetected) {
            this.piiDetected = piiDetected;
            return this;
        }

        public RecordSessionCallRequest build() {
            Objects.requireNonNull(apiKey, "apiKey is required");
            Objects.requireNonNull(callType, "callType is required");
            return new RecordSessionCallRequest(this);
        }
    }
}
