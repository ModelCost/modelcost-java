package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Request payload for tracking AI model usage.
 *
 * <p>Closed egress DTO: every field is safe telemetry or an opaque identifier ref.
 * There is intentionally no raw-content or free-form metadata field — that is the
 * architectural guarantee, proven by the egress invariant test.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public class TrackRequest {

    @JsonProperty("api_key")
    private final String apiKey;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("provider")
    private final String provider;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("feature")
    private final String feature;

    // Opaque, customer-controlled ref (HMAC-pseudonymized if a secret is set).
    // Never a raw email/MRN/name — see ai.modelcost.sdk.Identifiers.
    @JsonProperty("customer_id")
    private final String customerId;

    @JsonProperty("input_tokens")
    private final int inputTokens;

    @JsonProperty("output_tokens")
    private final int outputTokens;

    @JsonProperty("cache_creation_tokens")
    private final Integer cacheCreationTokens;

    @JsonProperty("cache_read_tokens")
    private final Integer cacheReadTokens;

    @JsonProperty("latency_ms")
    private final Long latencyMs;

    private TrackRequest(Builder builder) {
        this.apiKey = builder.apiKey;
        this.timestamp = builder.timestamp;
        this.provider = builder.provider;
        this.model = builder.model;
        this.feature = builder.feature;
        this.customerId = builder.customerId;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.cacheCreationTokens = builder.cacheCreationTokens;
        this.cacheReadTokens = builder.cacheReadTokens;
        this.latencyMs = builder.latencyMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getFeature() {
        return feature;
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public Integer getCacheCreationTokens() {
        return cacheCreationTokens;
    }

    public Integer getCacheReadTokens() {
        return cacheReadTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public static class Builder {
        private String apiKey;
        private Instant timestamp = Instant.now();
        private String provider;
        private String model;
        private String feature;
        private String customerId;
        private int inputTokens;
        private int outputTokens;
        private Integer cacheCreationTokens;
        private Integer cacheReadTokens;
        private Long latencyMs;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder feature(String feature) {
            this.feature = feature;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
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

        public Builder cacheCreationTokens(Integer cacheCreationTokens) {
            this.cacheCreationTokens = cacheCreationTokens;
            return this;
        }

        public Builder cacheReadTokens(Integer cacheReadTokens) {
            this.cacheReadTokens = cacheReadTokens;
            return this;
        }

        public Builder latencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        /**
         * @deprecated Removed in 0.4.0. The free-form metadata channel could carry
         *     arbitrary PII/PHI off-box, so it no longer exists. This setter throws to
         *     surface the change rather than silently drop data.
         */
        @Deprecated
        public Builder metadata(java.util.Map<String, Object> metadata) {
            throw new UnsupportedOperationException(
                    "TrackRequest.metadata was removed in modelcost 0.4.0 to make PII/PHI egress "
                            + "impossible. Use the opaque 'feature' / 'customerId' refs instead.");
        }

        public TrackRequest build() {
            Objects.requireNonNull(apiKey, "apiKey is required");
            Objects.requireNonNull(provider, "provider is required");
            Objects.requireNonNull(model, "model is required");
            return new TrackRequest(this);
        }
    }
}
