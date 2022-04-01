package id.global.core.router.model;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @author Tomaz Cerar
 */
public final class Subscribe extends id.global.iris.irissubscription.Subscribe {
    private final String token;

    public Subscribe(final ArrayNode resources, final String token) {
        super(resources);
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
