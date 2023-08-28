package org.iris_events.router.model.sub;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.iris_events.annotations.Message;

@RegisterForReflection
@Message(name = "subscribe-internal")
public record SubscribeInternal(@JsonProperty("resource_type") String resourceType,
                                @JsonProperty("resource_id") String resourceId) {
}
