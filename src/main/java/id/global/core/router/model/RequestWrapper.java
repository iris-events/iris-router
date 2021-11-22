package id.global.core.router.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tomaz Cerar
 */
public record RequestWrapper(String event,
        String clientTraceId,
        ObjectNode payload) {
}
