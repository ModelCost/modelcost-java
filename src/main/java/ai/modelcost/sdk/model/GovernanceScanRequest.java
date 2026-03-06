package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request payload for PII/governance scanning.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GovernanceScanRequest {

    @JsonProperty("org_id")
    private final String orgId;

    @JsonProperty("text")
    private final String text;

    @JsonProperty("feature")
    private final String feature;

    @JsonProperty("environment")
    private final String environment;

    private GovernanceScanRequest(Builder builder) {
        this.orgId = builder.orgId;
        this.text = builder.text;
        this.feature = builder.feature;
        this.environment = builder.environment;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOrgId() {
        return orgId;
    }

    public String getText() {
        return text;
    }

    public String getFeature() {
        return feature;
    }

    public String getEnvironment() {
        return environment;
    }

    public static class Builder {
        private String orgId;
        private String text;
        private String feature;
        private String environment;

        private Builder() {
        }

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder feature(String feature) {
            this.feature = feature;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public GovernanceScanRequest build() {
            Objects.requireNonNull(orgId, "orgId is required");
            Objects.requireNonNull(text, "text is required");
            return new GovernanceScanRequest(this);
        }
    }
}
