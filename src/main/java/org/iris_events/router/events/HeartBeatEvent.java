package org.iris_events.router.events;

public record HeartBeatEvent() implements RouterEvent {
    @Override
    public String getName() {
        return "heartbeat";
    }
}
