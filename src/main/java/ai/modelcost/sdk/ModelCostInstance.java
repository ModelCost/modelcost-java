package ai.modelcost.sdk;

import ai.modelcost.sdk.budget.BudgetManager;
import ai.modelcost.sdk.model.*;
import ai.modelcost.sdk.pii.PiiScanner;
import ai.modelcost.sdk.provider.AnthropicWrapper;
import ai.modelcost.sdk.provider.GoogleVertexWrapper;
import ai.modelcost.sdk.provider.OpenAIWrapper;
import ai.modelcost.sdk.provider.ProviderWrapper;
import ai.modelcost.sdk.session.SessionContext;
import ai.modelcost.sdk.tracking.CostTracker;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal singleton instance that holds all SDK state and dependencies.
 * Created and managed by the {@link ModelCost} static facade.
 */
class ModelCostInstance {

    private static final Logger logger = Logger.getLogger(ModelCostInstance.class.getName());

    private final ModelCostConfig config;
    private final ModelCostClient client;
    private final CostTracker costTracker;
    private final BudgetManager budgetManager;
    private final PiiScanner piiScanner;
    private final List<ProviderWrapper> providerWrappers;

    ModelCostInstance(ModelCostConfig config) {
        this.config = config;
        this.client = new ModelCostClient(config);
        this.costTracker = new CostTracker(config.getFlushBatchSize());
        this.budgetManager = new BudgetManager(config.getSyncIntervalMs());
        this.piiScanner = new PiiScanner();
        this.providerWrappers = List.of(
                new OpenAIWrapper(config, client, piiScanner),
                new AnthropicWrapper(config, client, piiScanner),
                new GoogleVertexWrapper(config, client, piiScanner)
        );

        // Synchronous pricing sync before anything uses calculateCost
        try {
            CostTracker.syncPricingFromApi(config.getBaseUrl(), config.getApiKey());
        } catch (Exception e) {
            logger.warning("Failed to sync pricing on init: " + e.getMessage());
        }

        // Start auto-flush
        costTracker.startAutoFlush(client, config.getFlushIntervalMs());

        // Start periodic pricing sync from server
        costTracker.startPricingSync(config.getBaseUrl(), config.getApiKey());

        logger.log(Level.INFO, "ModelCost SDK initialized for org {0} in {1} environment",
                new Object[]{config.getOrgId(), config.getEnvironment()});
    }

    @SuppressWarnings("unchecked")
    <T> T wrap(T providerClient) {
        for (ProviderWrapper wrapper : providerWrappers) {
            try {
                return wrapper.wrap(providerClient);
            } catch (Exception e) {
                // This wrapper doesn't support this client type, try next
                logger.log(Level.FINE, "Wrapper {0} does not support client type {1}",
                        new Object[]{wrapper.getProviderName(), providerClient.getClass().getName()});
            }
        }
        logger.log(Level.WARNING, "No suitable provider wrapper found for {0}; returning unwrapped client",
                providerClient.getClass().getName());
        return providerClient;
    }

    BudgetCheckResponse checkBudget(String scope, String id) {
        return budgetManager.check(client, config.getOrgId(), scope + ":" + id, 0.0);
    }

    GovernanceScanResponse scanPii(String text) {
        GovernanceScanRequest request = GovernanceScanRequest.builder()
                .orgId(config.getOrgId())
                .text(text)
                .environment(config.getEnvironment())
                .build();

        return client.scanText(request);
    }

    SessionContext startSession(String feature, Double maxSpendUsd, Integer maxIterations, String userId) {
        String sessionId = UUID.randomUUID().toString();
        SessionContext session = new SessionContext(sessionId, feature, userId, maxSpendUsd, maxIterations);

        try {
            CreateSessionRequest request = CreateSessionRequest.builder()
                    .apiKey(config.getApiKey())
                    .sessionId(sessionId)
                    .feature(feature)
                    .userId(userId)
                    .maxSpendUsd(maxSpendUsd)
                    .maxIterations(maxIterations)
                    .build();
            CreateSessionResponse response = client.createSession(request);
            if (response != null) {
                session.setServerSessionId(response.getId());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create server session (fail-open): {0}", e.getMessage());
        }

        return session;
    }

    void closeSession(SessionContext session, String reason) {
        session.close(reason);

        if (session.getServerSessionId() != null) {
            try {
                CloseSessionRequest request = CloseSessionRequest.builder()
                        .apiKey(config.getApiKey())
                        .status(session.getStatus())
                        .terminationReason(session.getTerminationReason())
                        .finalSpendUsd(session.getCurrentSpendUsd())
                        .finalIterationCount(session.getIterationCount())
                        .build();
                client.closeSession(session.getServerSessionId(), request);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to close server session (fail-open): {0}", e.getMessage());
            }
        }
    }

    void flush() {
        costTracker.flush(client);
    }

    void shutdown() {
        logger.log(Level.INFO, "Shutting down ModelCost SDK...");
        costTracker.flush(client);
        costTracker.shutdown();
        client.close();
        logger.log(Level.INFO, "ModelCost SDK shut down successfully");
    }

    ModelCostConfig getConfig() {
        return config;
    }

    ModelCostClient getClient() {
        return client;
    }

    CostTracker getCostTracker() {
        return costTracker;
    }

    BudgetManager getBudgetManager() {
        return budgetManager;
    }

    PiiScanner getPiiScanner() {
        return piiScanner;
    }
}
