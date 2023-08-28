package org.iris_events.router.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.iris_events.router.model.sub.Resource;

import java.util.List;



/**
 * @author Tomaz Cerar
 */
@RegisterForReflection
public final class Subscribe extends org.iris_events.router.model.sub.Subscribe {
    private final String token;
    private final Boolean heartbeat;

    public Subscribe(final List<Resource> resources, final String token, Boolean heartbeat) {
        super(resources);
        this.token = token;
        this.heartbeat = heartbeat;
    }

    public String getToken() {
        return token;
    }

    public Boolean getHeartbeat() {
        return heartbeat;
    }
}
