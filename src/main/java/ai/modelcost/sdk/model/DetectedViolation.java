package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single PII or governance violation detected in scanned text.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DetectedViolation {

    private String type;
    private String subtype;
    private String severity;
    private int start;
    private int end;

    public DetectedViolation() {
    }

    public DetectedViolation(String type, String subtype, String severity, int start, int end) {
        this.type = type;
        this.subtype = subtype;
        this.severity = severity;
        this.start = start;
        this.end = end;
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public String getSeverity() {
        return severity;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
