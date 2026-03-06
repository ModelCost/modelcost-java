package ai.modelcost.sdk;

import ai.modelcost.sdk.exception.ConfigurationException;
import ai.modelcost.sdk.model.BudgetCheckResponse;
import ai.modelcost.sdk.model.GovernanceScanResponse;
import ai.modelcost.sdk.session.SessionContext;

/**
 * Public static facade for the ModelCost SDK.
 * <p>
 * Usage:
 * <pre>
 *     ModelCost.init(ModelCostConfig.builder()
 *         .apiKey("mc_your_key")
 *         .orgId("org-123")
 *         .build());
 *
 *     var wrapped = ModelCost.wrap(openAiClient);
 *     var budget = ModelCost.checkBudget("org", "org-123");
 *     var scan = ModelCost.scanPii("text with PII");
 *
 *     ModelCost.shutdown();
 * </pre>
 */
public final class ModelCost {

    private static volatile ModelCostInstance instance;

    private ModelCost() {
        // Static utility class, not instantiable
    }

    /**
     * Initializes the ModelCost SDK with the given configuration.
     * Must be called before any other SDK method.
     *
     * @param config the SDK configuration
     * @throws ConfigurationException if config is null or invalid
     */
    public static void init(ModelCostConfig config) {
        if (config == null) {
            throw new ConfigurationException("ModelCostConfig must not be null");
        }
        synchronized (ModelCost.class) {
            if (instance != null) {
                instance.shutdown();
            }
            instance = new ModelCostInstance(config);
        }
    }

    /**
     * Wraps an AI provider client with usage-tracking instrumentation.
     *
     * @param providerClient the provider client to wrap
     * @param <T>            the type of the provider client
     * @return a wrapped proxy of the client
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static <T> T wrap(T providerClient) {
        ensureInitialized();
        return instance.wrap(providerClient);
    }

    /**
     * Performs a pre-flight budget check.
     *
     * @param scope the budget scope (e.g., "org", "team", "feature")
     * @param id    the scope identifier
     * @return the budget check response
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static BudgetCheckResponse checkBudget(String scope, String id) {
        ensureInitialized();
        return instance.checkBudget(scope, id);
    }

    /**
     * Scans text for PII using the governance API.
     *
     * @param text the text to scan
     * @return the governance scan response
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static GovernanceScanResponse scanPii(String text) {
        ensureInitialized();
        return instance.scanPii(text);
    }

    /**
     * Starts a new agent session with optional budget and iteration limits.
     *
     * @param feature       the feature name for this session
     * @param maxSpendUsd   optional maximum spend in USD (null for unlimited)
     * @param maxIterations optional maximum number of iterations (null for unlimited)
     * @param userId        optional user identifier
     * @return a new SessionContext for tracking the session
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static SessionContext startSession(String feature, Double maxSpendUsd, Integer maxIterations, String userId) {
        ensureInitialized();
        return instance.startSession(feature, maxSpendUsd, maxIterations, userId);
    }

    /**
     * Closes a session with a "completed" status.
     *
     * @param session the session to close
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static void closeSession(SessionContext session) {
        closeSession(session, "completed");
    }

    /**
     * Closes a session with the given reason.
     *
     * @param session the session to close
     * @param reason  the closure reason (e.g., "completed", "budget_exceeded", "user_cancelled")
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static void closeSession(SessionContext session, String reason) {
        ensureInitialized();
        instance.closeSession(session, reason);
    }

    /**
     * Forces an immediate flush of all buffered telemetry data.
     *
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static void flush() {
        ensureInitialized();
        instance.flush();
    }

    /**
     * Shuts down the SDK, flushing pending data and releasing resources.
     * After calling this, the SDK must be re-initialized with {@link #init(ModelCostConfig)}.
     *
     * @throws ConfigurationException if the SDK has not been initialized
     */
    public static void shutdown() {
        ensureInitialized();
        synchronized (ModelCost.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    /**
     * Returns whether the SDK has been initialized.
     *
     * @return true if {@link #init(ModelCostConfig)} has been called
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    private static void ensureInitialized() {
        if (instance == null) {
            throw new ConfigurationException(
                    "ModelCost SDK has not been initialized. Call ModelCost.init(config) first.");
        }
    }

    // Visible for testing: reset the singleton
    static void reset() {
        synchronized (ModelCost.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }
}
