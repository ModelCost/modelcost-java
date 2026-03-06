package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the budget status endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetStatusResponse {

    private List<BudgetPolicy> policies;

    @JsonProperty("total_budget_usd")
    private double totalBudgetUsd;

    @JsonProperty("total_spend_usd")
    private double totalSpendUsd;

    @JsonProperty("policies_at_risk")
    private int policiesAtRisk;

    public BudgetStatusResponse() {
    }

    public List<BudgetPolicy> getPolicies() {
        return policies;
    }

    public double getTotalBudgetUsd() {
        return totalBudgetUsd;
    }

    public double getTotalSpendUsd() {
        return totalSpendUsd;
    }

    public int getPoliciesAtRisk() {
        return policiesAtRisk;
    }
}
