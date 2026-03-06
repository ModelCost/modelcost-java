package ai.modelcost.sdk.session;

import java.time.Instant;

/**
 * Immutable record of a single call within an agent session.
 */
public record SessionCallRecord(
    int callSequence,
    String callType,
    String toolName,
    int inputTokens,
    int outputTokens,
    int cumulativeInputTokens,
    double costUsd,
    double cumulativeCostUsd,
    boolean piiDetected,
    Instant createdAt
) {}
