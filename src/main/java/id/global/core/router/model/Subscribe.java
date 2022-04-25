package id.global.core.router.model;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @author Tomaz Cerar
 */
public final class Subscribe extends id.global.iris.irissubscription.Subscribe {
    private final String token;
    private final Boolean heartbeat;

    public Subscribe(final ArrayNode resources, final String token, Boolean heartbeat) {
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
