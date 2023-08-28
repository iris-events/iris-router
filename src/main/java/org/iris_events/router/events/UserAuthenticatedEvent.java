package org.iris_events.router.events;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UserAuthenticatedEvent() implements RouterEvent {

    public static final String NAME = "user-authenticated";

    @Override
    public String getName() {
        return NAME;
    }
}
