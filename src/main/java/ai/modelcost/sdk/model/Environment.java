package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported deployment environments.
 */
public enum Environment {

    PRODUCTION("production"),
    STAGING("staging"),
    DEVELOPMENT("development");

    private final String value;

    Environment(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Environment fromValue(String value) {
        for (Environment env : values()) {
            if (env.value.equalsIgnoreCase(value)) {
                return env;
            }
        }
        throw new IllegalArgumentException("Unknown environment: " + value);
    }
}
