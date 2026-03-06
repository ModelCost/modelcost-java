package ai.modelcost.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from the track endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackResponse {

    private String status;

    public TrackResponse() {
    }

    public TrackResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
