package org.iris_events.router.model.sub;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record Resource(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId) {
}
