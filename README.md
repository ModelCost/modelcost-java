# ModelCost Java SDK

Java SDK for the ModelCost AI cost management platform. Track, manage, and optimize your AI model spending.

## Requirements

- Java 17+
- Maven 3.8+

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>ai.modelcost</groupId>
    <artifactId>modelcost-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quickstart

```java
import ai.modelcost.sdk.ModelCost;
import ai.modelcost.sdk.ModelCostConfig;

// Initialize with builder
ModelCostConfig config = ModelCostConfig.builder()
    .apiKey("mc_your_api_key")
    .orgId("org-123")
    .monthlyBudget(500.0)
    .build();

ModelCost.init(config);

// Or initialize from environment variables
// export MODELCOST_API_KEY=mc_your_api_key
// export MODELCOST_ORG_ID=org-123
ModelCost.init(ModelCostConfig.fromEnv());

// Wrap your AI provider client for automatic tracking
var wrappedClient = ModelCost.wrap(openAiClient);

// Pre-flight budget check
var budgetCheck = ModelCost.checkBudget("org", "org-123");
if (budgetCheck.isAllowed()) {
    // proceed with API call
}

// PII scanning
var scanResult = ModelCost.scanPii("My SSN is 123-45-6789");

// Shutdown when done
ModelCost.shutdown();
```

## Configuration

| Option | Environment Variable | Default | Description |
|--------|---------------------|---------|-------------|
| apiKey | MODELCOST_API_KEY | (required) | API key starting with `mc_` |
| orgId | MODELCOST_ORG_ID | (required) | Organization identifier |
| environment | MODELCOST_ENV | production | Environment name |
| baseUrl | MODELCOST_BASE_URL | https://api.modelcost.ai | API base URL |
| monthlyBudget | - | null | Monthly budget cap in USD |
| budgetAction | - | ALERT | Action on budget exceed |
| failOpen | - | true | Continue on SDK errors |
| flushIntervalMs | - | 5000 | Telemetry flush interval |
| flushBatchSize | - | 100 | Telemetry batch size |
| syncIntervalMs | - | 10000 | Budget sync interval |

## Building

```bash
mvn clean install
```

## Testing

```bash
mvn test
```
