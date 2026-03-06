package ai.modelcost.sdk;

import ai.modelcost.sdk.exception.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelCostConfigTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("MODELCOST_API_KEY");
        System.clearProperty("MODELCOST_ORG_ID");
        System.clearProperty("MODELCOST_ENV");
        System.clearProperty("MODELCOST_BASE_URL");
    }

    @Test
    void builderWithValidConfigSucceeds() {
        ModelCostConfig config = ModelCostConfig.builder()
                .apiKey("mc_test_key_123")
                .orgId("org-456")
                .environment("staging")
                .baseUrl("https://custom.api.example.com")
                .monthlyBudget(1000.0)
                .budgetAction(ModelCostConfig.BudgetAction.HARD_STOP)
                .failOpen(false)
                .flushIntervalMs(10000)
                .flushBatchSize(50)
                .syncIntervalMs(20000)
                .build();

        assertEquals("mc_test_key_123", config.getApiKey());
        assertEquals("org-456", config.getOrgId());
        assertEquals("staging", config.getEnvironment());
        assertEquals("https://custom.api.example.com", config.getBaseUrl());
        assertEquals(1000.0, config.getMonthlyBudget());
        assertEquals(ModelCostConfig.BudgetAction.HARD_STOP, config.getBudgetAction());
        assertFalse(config.isFailOpen());
        assertEquals(10000, config.getFlushIntervalMs());
        assertEquals(50, config.getFlushBatchSize());
        assertEquals(20000, config.getSyncIntervalMs());
    }

    @Test
    void missingApiKeyThrowsConfigurationException() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                ModelCostConfig.builder()
                        .orgId("org-123")
                        .build()
        );
        assertTrue(ex.getMessage().contains("API key is required"));
    }

    @Test
    void invalidApiKeyPrefixThrowsConfigurationException() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                ModelCostConfig.builder()
                        .apiKey("sk_invalid_prefix")
                        .orgId("org-123")
                        .build()
        );
        assertTrue(ex.getMessage().contains("must start with 'mc_'"));
    }

    @Test
    void blankOrgIdThrowsConfigurationException() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                ModelCostConfig.builder()
                        .apiKey("mc_valid_key")
                        .orgId("  ")
                        .build()
        );
        assertTrue(ex.getMessage().contains("Organization ID is required"));
    }

    @Test
    void fromEnvReadsSystemProperties() {
        System.setProperty("MODELCOST_API_KEY", "mc_env_key_789");
        System.setProperty("MODELCOST_ORG_ID", "org-env-123");
        System.setProperty("MODELCOST_ENV", "development");
        System.setProperty("MODELCOST_BASE_URL", "https://env.api.example.com");

        ModelCostConfig config = ModelCostConfig.fromEnv();

        assertEquals("mc_env_key_789", config.getApiKey());
        assertEquals("org-env-123", config.getOrgId());
        assertEquals("development", config.getEnvironment());
        assertEquals("https://env.api.example.com", config.getBaseUrl());
    }

    @Test
    void defaultValuesAreCorrect() {
        ModelCostConfig config = ModelCostConfig.builder()
                .apiKey("mc_test_key")
                .orgId("org-123")
                .build();

        assertEquals("production", config.getEnvironment());
        assertEquals("https://api.modelcost.ai", config.getBaseUrl());
        assertNull(config.getMonthlyBudget());
        assertEquals(ModelCostConfig.BudgetAction.ALERT, config.getBudgetAction());
        assertTrue(config.isFailOpen());
        assertEquals(5000, config.getFlushIntervalMs());
        assertEquals(100, config.getFlushBatchSize());
        assertEquals(10000, config.getSyncIntervalMs());
    }
}
