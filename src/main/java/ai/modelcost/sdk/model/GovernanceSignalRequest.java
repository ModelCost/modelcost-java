package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Request payload for reporting governance classification signals (metadata-only mode).
 * No raw text is included — only classification results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GovernanceSignalRequest {

    @JsonProperty("organizationId")
    private final String organizationId;

    @JsonProperty("violationType")
    private final String violationType;

    @JsonProperty("violationSubtype")
    private final String violationSubtype;

    @JsonProperty("severity")
    private final String severity;

    @JsonProperty("environment")
    private final String environment;

    @JsonProperty("actionTaken")
    private final String actionTaken;

    @JsonProperty("wasAllowed")
    private final boolean wasAllowed;

    @JsonProperty("detectedAt")
    private final String detectedAt;

    @JsonProperty("source")
    private final String source;

    @JsonProperty("violationCount")
    private final int violationCount;

    private GovernanceSignalRequest(Builder builder) {
        this.organizationId = builder.organizationId;
        this.violationType = builder.violationType;
        this.violationSubtype = builder.violationSubtype;
        this.severity = builder.severity;
        this.environment = builder.environment;
        this.actionTaken = builder.actionTaken;
        this.wasAllowed = builder.wasAllowed;
        this.detectedAt = builder.detectedAt;
        this.source = builder.source;
        this.violationCount = builder.violationCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOrganizationId() { return organizationId; }
    public String getViolationType() { return violationType; }
    public String getViolationSubtype() { return violationSubtype; }
    public String getSeverity() { return severity; }
    public String getEnvironment() { return environment; }
    public String getActionTaken() { return actionTaken; }
    public boolean isWasAllowed() { return wasAllowed; }
    public String getDetectedAt() { return detectedAt; }
    public String getSource() { return source; }
    public int getViolationCount() { return violationCount; }

    public static class Builder {
        private String organizationId;
        private String violationType;
        private String violationSubtype;
        private String severity;
        private String environment;
        private String actionTaken;
        private boolean wasAllowed;
        private String detectedAt;
        private String source = "metadata_only";
        private int violationCount = 1;

        private Builder() {}

        public Builder organizationId(String organizationId) { this.organizationId = organizationId; return this; }
        public Builder violationType(String violationType) { this.violationType = violationType; return this; }
        public Builder violationSubtype(String violationSubtype) { this.violationSubtype = violationSubtype; return this; }
        public Builder severity(String severity) { this.severity = severity; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder actionTaken(String actionTaken) { this.actionTaken = actionTaken; return this; }
        public Builder wasAllowed(boolean wasAllowed) { this.wasAllowed = wasAllowed; return this; }
        public Builder detectedAt(String detectedAt) { this.detectedAt = detectedAt; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder violationCount(int violationCount) { this.violationCount = violationCount; return this; }

        public GovernanceSignalRequest build() {
            Objects.requireNonNull(organizationId, "organizationId is required");
            Objects.requireNonNull(violationType, "violationType is required");
            Objects.requireNonNull(violationSubtype, "violationSubtype is required");
            Objects.requireNonNull(severity, "severity is required");
            return new GovernanceSignalRequest(this);
        }
    }
}
