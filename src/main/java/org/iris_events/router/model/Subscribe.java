package org.iris_events.router.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.iris_events.router.model.sub.Resource;

import java.util.List;



/**
 * @author Tomaz Cerar
 */
@RegisterForReflection
public final class Subscribe extends org.iris_events.router.model.sub.Subscribe {
    private final String token;
    @JsonProperty("device_id")
    private final String deviceId;
    private final Boolean heartbeat;

    public Subscribe(final List<Resource> resources, final String token, String deviceId, Boolean heartbeat) {
        super(resources);
        this.token = token;
        this.deviceId = deviceId;
        this.heartbeat = heartbeat;
    }

    public String getToken() {
        return token;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Boolean getHeartbeat() {
        return heartbeat;
    }
}
