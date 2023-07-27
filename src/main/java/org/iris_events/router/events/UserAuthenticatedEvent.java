package org.iris_events.router.events;

public record UserAuthenticatedEvent() implements RouterEvent {

    public static final String NAME = "user-authenticated";

    @Override
    public String getName() {
        return NAME;
    }
}
