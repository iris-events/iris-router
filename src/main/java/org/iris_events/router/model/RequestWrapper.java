package org.iris_events.router.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tomaz Cerar
 */
public record RequestWrapper(String event,
        @JsonProperty("client_trace_id") @JsonAlias( {
                "clientTraceId" }) String clientTraceId,
        @JsonProperty("correlation_id") @JsonAlias({ "correlationId" }) String correlationId,
        @JsonProperty("payload") ObjectNode payload){

    public RequestWrapper(final String event, final String clientTraceId, final ObjectNode payload) {
        this(event, clientTraceId, UUID.randomUUID().toString(), payload);
    }

    public RequestWrapper withCorrelationId(String correlationId) {
        return new RequestWrapper(event(), clientTraceId(), correlationId, payload());
    }
}
