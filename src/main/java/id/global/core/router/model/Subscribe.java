package id.global.core.router.model;

import java.util.List;

import id.global.iris.irissubscription.payload.Resource;

/**
 * @author Tomaz Cerar
 */
public final class Subscribe extends id.global.iris.irissubscription.Subscribe {
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
