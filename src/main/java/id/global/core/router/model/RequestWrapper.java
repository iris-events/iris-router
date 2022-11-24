package id.global.core.router.model;

import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tomaz Cerar
 */
public record RequestWrapper(String event,
        String clientTraceId,
        String correlationId,
        ObjectNode payload) {

    public RequestWrapper(final String event, final String clientTraceId, final ObjectNode payload) {
        this(event, clientTraceId, UUID.randomUUID().toString(), payload);
    }

    public RequestWrapper withCorrelationId(String correlationId) {
        return new RequestWrapper(event(), clientTraceId(), correlationId, payload());
    }
}
