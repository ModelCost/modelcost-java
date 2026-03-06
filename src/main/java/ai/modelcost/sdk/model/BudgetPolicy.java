package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Budget policy definition matching the OpenAPI spec.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetPolicy {

    private String id;
    private String name;
    private String scope;

    @JsonProperty("scope_identifier")
    private String scopeIdentifier;

    @JsonProperty("budget_amount_usd")
    private double budgetAmountUsd;

    private String period;

    @JsonProperty("custom_period_days")
    private Integer customPeriodDays;

    private String action;

    @JsonProperty("throttle_percentage")
    private Integer throttlePercentage;

    @JsonProperty("alert_thresholds")
    private List<Integer> alertThresholds;

    @JsonProperty("current_spend_usd")
    private double currentSpendUsd;

    @JsonProperty("spend_percentage")
    private double spendPercentage;

    @JsonProperty("period_start")
    private Instant periodStart;

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public BudgetPolicy() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getScope() {
        return scope;
    }

    public String getScopeIdentifier() {
        return scopeIdentifier;
    }

    public double getBudgetAmountUsd() {
        return budgetAmountUsd;
    }

    public String getPeriod() {
        return period;
    }

    public Integer getCustomPeriodDays() {
        return customPeriodDays;
    }

    public String getAction() {
        return action;
    }

    public Integer getThrottlePercentage() {
        return throttlePercentage;
    }

    public List<Integer> getAlertThresholds() {
        return alertThresholds;
    }

    public double getCurrentSpendUsd() {
        return currentSpendUsd;
    }

    public double getSpendPercentage() {
        return spendPercentage;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
