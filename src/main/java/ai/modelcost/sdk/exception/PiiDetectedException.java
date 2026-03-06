package ai.modelcost.sdk.exception;

import ai.modelcost.sdk.model.DetectedViolation;

import java.util.List;

/**
 * Thrown when PII is detected in text that should not contain it.
 */
public class PiiDetectedException extends ModelCostException {

    private final List<DetectedViolation> detectedEntities;
    private final String redactedText;

    public PiiDetectedException(String message, List<DetectedViolation> detectedEntities, String redactedText) {
        super(message);
        this.detectedEntities = detectedEntities;
        this.redactedText = redactedText;
    }

    public List<DetectedViolation> getDetectedEntities() {
        return detectedEntities;
    }

    public String getRedactedText() {
        return redactedText;
    }
}
