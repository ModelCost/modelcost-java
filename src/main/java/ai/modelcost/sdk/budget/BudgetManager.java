package ai.modelcost.sdk.budget;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.model.BudgetCheckResponse;
import ai.modelcost.sdk.model.BudgetPolicy;
import ai.modelcost.sdk.model.BudgetStatusResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages budget state locally with periodic synchronization against the server.
 * Provides fast local budget checks with optimistic updates.
 */
public class BudgetManager {

    private static final Logger logger = Logger.getLogger(BudgetManager.class.getName());

    private final ConcurrentHashMap<String, BudgetStatusResponse> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastSyncTimestamp = new AtomicLong(0);
    private final long syncIntervalMs;
    private final ReentrantLock syncLock = new ReentrantLock();

    public BudgetManager(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    /**
     * Checks the budget for a given organization and feature.
     * Returns the server response if available, or performs a sync if the cache is stale.
     *
     * @param client        the ModelCost API client
     * @param orgId         the organization ID
     * @param feature       the feature being used
     * @param estimatedCost the estimated cost of the upcoming operation
     * @return the budget check response
     */
    public BudgetCheckResponse check(ModelCostClient client, String orgId, String feature, double estimatedCost) {
        // Sync if stale
        long now = System.currentTimeMillis();
        if (now - lastSyncTimestamp.get() > syncIntervalMs) {
            sync(client, orgId);
        }

        // Check local cache first for a quick decision
        BudgetStatusResponse status = cache.get(orgId);
        if (status != null) {
            double remaining = status.getTotalBudgetUsd() - status.getTotalSpendUsd();
            if (remaining < estimatedCost) {
                // Budget would be exceeded, delegate to server for authoritative decision
                return client.checkBudget(orgId, feature, estimatedCost);
            }
        }

        // Delegate to server for authoritative check
        return client.checkBudget(orgId, feature, estimatedCost);
    }

    /**
     * Synchronizes the local budget cache with the server.
     *
     * @param client the ModelCost API client
     * @param orgId  the organization ID
     */
    public void sync(ModelCostClient client, String orgId) {
        if (!syncLock.tryLock()) {
            return; // Another thread is already syncing
        }
        try {
            BudgetStatusResponse status = client.getBudgetStatus(orgId);
            if (status != null) {
                cache.put(orgId, status);
                lastSyncTimestamp.set(System.currentTimeMillis());
                logger.log(Level.FINE, "Budget cache synced for org {0}: spend={1}/{2}",
                        new Object[]{orgId, status.getTotalSpendUsd(), status.getTotalBudgetUsd()});
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to sync budget status for org " + orgId, e);
        } finally {
            syncLock.unlock();
        }
    }

    /**
     * Optimistically updates the local spend for a given organization.
     * This allows the local cache to reflect recent usage without waiting for a sync.
     *
     * @param orgId   the organization ID
     * @param feature the feature name (reserved for future per-feature tracking)
     * @param cost    the cost to add to the local spend total
     */
    public void updateLocalSpend(String orgId, String feature, double cost) {
        cache.computeIfPresent(orgId, (key, status) -> {
            // We cannot mutate the immutable response object, so we create an updated view
            // by wrapping the data. For simplicity, we use a reflection-free approach:
            // store the updated spend in a thread-safe side map.
            // However, since BudgetStatusResponse uses Jackson deserialization with default
            // constructor, we can work around this by keeping a separate spend offset map.
            // For now, we log the optimistic update.
            logger.log(Level.FINE, "Optimistic spend update for org {0}, feature {1}: +${2}",
                    new Object[]{orgId, feature, cost});
            return status;
        });
        // Track cumulative optimistic spend offsets
        spendOffsets.merge(orgId, cost, Double::sum);
    }

    /**
     * Gets the effective total spend for an org including local optimistic updates.
     */
    public double getEffectiveSpend(String orgId) {
        BudgetStatusResponse status = cache.get(orgId);
        double baseSpend = status != null ? status.getTotalSpendUsd() : 0.0;
        double offset = spendOffsets.getOrDefault(orgId, 0.0);
        return baseSpend + offset;
    }

    // Tracks optimistic spend offsets between syncs
    private final ConcurrentHashMap<String, Double> spendOffsets = new ConcurrentHashMap<>();

    /**
     * Returns the cached budget status for an organization, or null if not cached.
     */
    public BudgetStatusResponse getCachedStatus(String orgId) {
        return cache.get(orgId);
    }

    /**
     * Returns the timestamp of the last successful sync.
     */
    public long getLastSyncTimestamp() {
        return lastSyncTimestamp.get();
    }

    // Visible for testing
    void setLastSyncTimestamp(long timestamp) {
        lastSyncTimestamp.set(timestamp);
    }

    // Visible for testing
    void putCachedStatus(String orgId, BudgetStatusResponse status) {
        cache.put(orgId, status);
    }
}
