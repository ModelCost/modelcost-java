package ai.modelcost.sdk.session;

import ai.modelcost.sdk.exception.SessionBudgetExceededException;
import ai.modelcost.sdk.exception.SessionIterationLimitExceededException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe context for tracking an agent session's budget, iterations, and call history.
 * <p>
 * Each session has optional budget and iteration limits. Before each call,
 * {@link #preCallCheck(double)} validates that the session can proceed.
 * After each call, {@link #recordCall} updates the running totals.
 */
public class SessionContext {

    private final String sessionId;
    private volatile String serverSessionId;
    private final String feature;
    private final String userId;
    private final Double maxSpendUsd;
    private final Integer maxIterations;

    private double currentSpendUsd = 0.0;
    private int iterationCount = 0;
    private int cumulativeInputTokens = 0;
    private String status = "active";
    private String terminationReason;
    private final List<SessionCallRecord> calls = new ArrayList<>();

    public SessionContext(String sessionId, String feature, String userId,
                          Double maxSpendUsd, Integer maxIterations) {
        this.sessionId = sessionId;
        this.feature = feature;
        this.userId = userId;
        this.maxSpendUsd = maxSpendUsd;
        this.maxIterations = maxIterations;
    }

    /**
     * Validates that the session can accept another call at the estimated cost.
     *
     * @param estimatedCost the estimated cost in USD for the next call
     * @throws SessionBudgetExceededException       if the budget would be exceeded
     * @throws SessionIterationLimitExceededException if the iteration limit has been reached
     */
    public synchronized void preCallCheck(double estimatedCost) {
        if (!"active".equals(status)) {
            throw new SessionBudgetExceededException(
                "Session is not active (status: " + status + ")",
                sessionId, currentSpendUsd, maxSpendUsd != null ? maxSpendUsd : 0.0);
        }
        if (maxSpendUsd != null && currentSpendUsd + estimatedCost > maxSpendUsd) {
            status = "terminated";
            terminationReason = "budget_exceeded";
            throw new SessionBudgetExceededException(
                "Session budget exceeded: $" + String.format("%.4f", currentSpendUsd) +
                " + $" + String.format("%.4f", estimatedCost) + " > $" + String.format("%.2f", maxSpendUsd),
                sessionId, currentSpendUsd, maxSpendUsd);
        }
        if (maxIterations != null && iterationCount >= maxIterations) {
            status = "terminated";
            terminationReason = "iteration_limit";
            throw new SessionIterationLimitExceededException(
                "Session iteration limit reached: " + iterationCount + " >= " + maxIterations,
                sessionId, iterationCount, maxIterations);
        }
    }

    /**
     * Records a completed call and updates running totals.
     *
     * @param callType     the type of call (e.g., "llm", "tool")
     * @param inputTokens  number of input tokens consumed
     * @param outputTokens number of output tokens produced
     * @param costUsd      cost of this call in USD
     * @return the recorded call
     */
    public synchronized SessionCallRecord recordCall(String callType, int inputTokens,
                                                      int outputTokens, double costUsd) {
        return recordCall(callType, inputTokens, outputTokens, costUsd, null);
    }

    /**
     * Records a completed call and updates running totals.
     *
     * @param callType     the type of call (e.g., "llm", "tool")
     * @param inputTokens  number of input tokens consumed
     * @param outputTokens number of output tokens produced
     * @param costUsd      cost of this call in USD
     * @param toolName     optional tool name (for tool calls)
     * @return the recorded call
     */
    public synchronized SessionCallRecord recordCall(String callType, int inputTokens,
                                                      int outputTokens, double costUsd, String toolName) {
        iterationCount++;
        currentSpendUsd += costUsd;
        cumulativeInputTokens += inputTokens;

        SessionCallRecord record = new SessionCallRecord(
            iterationCount, callType, toolName,
            inputTokens, outputTokens, cumulativeInputTokens,
            costUsd, currentSpendUsd, false, Instant.now()
        );
        calls.add(record);
        return record;
    }

    /**
     * Closes the session with the given reason.
     *
     * @param reason the closure reason (e.g., "completed", "budget_exceeded", "user_cancelled")
     */
    public synchronized void close(String reason) {
        if ("active".equals(status)) {
            status = "completed".equals(reason) ? "completed" : "terminated";
            if (!"completed".equals(reason)) {
                terminationReason = reason;
            }
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String getSessionId() {
        return sessionId;
    }

    public String getServerSessionId() {
        return serverSessionId;
    }

    public void setServerSessionId(String id) {
        this.serverSessionId = id;
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

    public synchronized double getCurrentSpendUsd() {
        return currentSpendUsd;
    }

    public synchronized int getIterationCount() {
        return iterationCount;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized String getTerminationReason() {
        return terminationReason;
    }

    public synchronized List<SessionCallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    /**
     * Returns the remaining budget in USD, or {@code null} if no budget limit is set.
     */
    public synchronized Double getRemainingBudget() {
        return maxSpendUsd != null ? maxSpendUsd - currentSpendUsd : null;
    }

    /**
     * Returns the remaining iterations, or {@code null} if no iteration limit is set.
     */
    public synchronized Integer getRemainingIterations() {
        return maxIterations != null ? maxIterations - iterationCount : null;
    }
}
