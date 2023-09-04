package org.iris_events.router.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UserAuthenticatedEvent() implements RouterEvent {

    public static final String NAME = "user-authenticated";

    @Override
    @JsonIgnore
    public String getName() {
        return NAME;
    }
}
