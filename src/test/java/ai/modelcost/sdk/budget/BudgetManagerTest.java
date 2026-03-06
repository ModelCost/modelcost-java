package ai.modelcost.sdk.budget;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.model.BudgetCheckResponse;
import ai.modelcost.sdk.model.BudgetStatusResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BudgetManagerTest {

    private BudgetManager budgetManager;
    private ModelCostClient mockClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        budgetManager = new BudgetManager(10_000); // 10s sync interval
        mockClient = Mockito.mock(ModelCostClient.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void checkReturnsCachedResultWhenFresh() throws Exception {
        // Populate cache with a recent sync
        String statusJson = """
                {
                    "policies": [],
                    "total_budget_usd": 1000.0,
                    "total_spend_usd": 200.0,
                    "policies_at_risk": 0
                }
                """;
        BudgetStatusResponse cachedStatus = objectMapper.readValue(statusJson, BudgetStatusResponse.class);
        budgetManager.putCachedStatus("org-123", cachedStatus);
        budgetManager.setLastSyncTimestamp(System.currentTimeMillis()); // Mark as just synced

        // Mock the check budget call
        BudgetCheckResponse mockResponse = objectMapper.readValue(
                "{\"allowed\":true,\"action\":null,\"reason\":\"within budget\"}",
                BudgetCheckResponse.class
        );
        when(mockClient.checkBudget(eq("org-123"), anyString(), anyDouble())).thenReturn(mockResponse);

        BudgetCheckResponse result = budgetManager.check(mockClient, "org-123", "chat", 0.5);

        assertNotNull(result);
        assertTrue(result.isAllowed());
        // Should NOT have called getBudgetStatus since cache is fresh
        verify(mockClient, never()).getBudgetStatus(anyString());
    }

    @Test
    void checkTriggersSyncWhenStale() throws Exception {
        // Set last sync to far in the past (stale)
        budgetManager.setLastSyncTimestamp(System.currentTimeMillis() - 60_000);

        BudgetStatusResponse statusResponse = objectMapper.readValue("""
                {
                    "policies": [],
                    "total_budget_usd": 500.0,
                    "total_spend_usd": 100.0,
                    "policies_at_risk": 0
                }
                """, BudgetStatusResponse.class);
        when(mockClient.getBudgetStatus("org-123")).thenReturn(statusResponse);

        BudgetCheckResponse checkResponse = objectMapper.readValue(
                "{\"allowed\":true,\"reason\":\"ok\"}",
                BudgetCheckResponse.class
        );
        when(mockClient.checkBudget(eq("org-123"), anyString(), anyDouble())).thenReturn(checkResponse);

        BudgetCheckResponse result = budgetManager.check(mockClient, "org-123", "chat", 1.0);

        assertNotNull(result);
        assertTrue(result.isAllowed());
        // Should have called getBudgetStatus to sync
        verify(mockClient).getBudgetStatus("org-123");
    }

    @Test
    void updateLocalSpendModifiesCache() throws Exception {
        String statusJson = """
                {
                    "policies": [],
                    "total_budget_usd": 1000.0,
                    "total_spend_usd": 500.0,
                    "policies_at_risk": 0
                }
                """;
        BudgetStatusResponse cachedStatus = objectMapper.readValue(statusJson, BudgetStatusResponse.class);
        budgetManager.putCachedStatus("org-123", cachedStatus);

        budgetManager.updateLocalSpend("org-123", "chat", 10.0);
        budgetManager.updateLocalSpend("org-123", "summarize", 5.0);

        double effectiveSpend = budgetManager.getEffectiveSpend("org-123");
        assertEquals(515.0, effectiveSpend, 0.001);
    }
}
