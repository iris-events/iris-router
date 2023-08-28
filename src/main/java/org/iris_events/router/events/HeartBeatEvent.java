package org.iris_events.router.events;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record HeartBeatEvent() implements RouterEvent {
    @Override
    public String getName() {
        return "heartbeat";
    }
}
