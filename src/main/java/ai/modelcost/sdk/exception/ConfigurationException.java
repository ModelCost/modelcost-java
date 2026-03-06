package ai.modelcost.sdk.exception;

/**
 * Thrown when the SDK is misconfigured or missing required configuration values.
 * This is a fatal error that prevents the SDK from operating.
 */
public class ConfigurationException extends ModelCostException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
