package org.iris_events.router.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Tomaz Cerar
 */
@RegisterForReflection
public record RequestWrapper(String event,
        @JsonProperty("client_trace_id") @JsonAlias( { "clientTraceId" }) String clientTraceId,
        @JsonProperty("payload") ObjectNode payload){
}
