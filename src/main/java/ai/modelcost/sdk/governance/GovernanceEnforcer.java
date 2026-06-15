package ai.modelcost.sdk.governance;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.ModelCostConfig;
import ai.modelcost.sdk.exception.PiiDetectedException;
import ai.modelcost.sdk.model.DetectedViolation;
import ai.modelcost.sdk.model.GovernanceSignalRequest;
import ai.modelcost.sdk.pii.PiiScanner;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local-only governance enforcement shared by all provider wrappers.
 *
 * <p>Content is scanned entirely in-process; raw text is never transmitted. When
 * violations are found we (1) emit de-identified, aggregated signals and (2) block
 * the call by throwing {@link PiiDetectedException}. There is deliberately no
 * server-side content scan — that capability was removed so prompt/response content
 * cannot leave the customer environment by any path.
 */
public final class GovernanceEnforcer {

    private static final Logger logger = Logger.getLogger(GovernanceEnforcer.class.getName());

    private GovernanceEnforcer() {
    }

    /**
     * Scans {@code text} locally; signals + blocks if violations are found. Never
     * transmits {@code text}.
     */
    public static void enforceLocal(String text, ModelCostConfig config,
                                    ModelCostClient mcClient, PiiScanner piiScanner) {
        if (piiScanner == null || text == null || text.isEmpty()) {
            return;
        }
        PiiScanner.FullScanResult full = piiScanner.fullScan(text, null);
        if (!full.isDetected()) {
            return;
        }

        if (config != null && mcClient != null) {
            // Aggregate by (category, subtype, severity): one signal per distinct
            // violation kind instead of one network call per hit (no N+1).
            Map<String, int[]> counts = new LinkedHashMap<>();
            Map<String, PiiScanner.GovernanceViolation> exemplar = new LinkedHashMap<>();
            for (PiiScanner.GovernanceViolation v : full.getViolations()) {
                String key = v.getCategory() + "|" + v.getType() + "|" + v.getSeverity();
                counts.computeIfAbsent(key, k -> new int[1])[0]++;
                exemplar.putIfAbsent(key, v);
            }
            String detectedAt = Instant.now().toString();
            for (Map.Entry<String, int[]> e : counts.entrySet()) {
                PiiScanner.GovernanceViolation v = exemplar.get(e.getKey());
                try {
                    mcClient.reportSignal(GovernanceSignalRequest.builder()
                            .organizationId(config.getOrgId())
                            .violationType(v.getCategory())
                            .violationSubtype(v.getType())
                            .severity(v.getSeverity())
                            .environment(config.getEnvironment())
                            .actionTaken("block")
                            .wasAllowed(false)
                            .detectedAt(detectedAt)
                            .source("metadata_only")
                            .violationCount(e.getValue()[0])
                            .build());
                } catch (Exception ignored) {
                    logger.log(Level.FINE, "Failed to report governance signal (fire-and-forget)");
                }
            }
        }

        List<DetectedViolation> entities = full.getViolations().stream()
                .map(v -> new DetectedViolation(v.getCategory(), v.getType(),
                        v.getSeverity(), v.getStart(), v.getEnd()))
                .toList();

        throw new PiiDetectedException(
                "Sensitive content detected and blocked locally (content never transmitted)",
                entities,
                piiScanner.redact(text));
    }
}
