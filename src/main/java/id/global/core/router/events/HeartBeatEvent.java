package id.global.core.router.events;

public record HeartBeatEvent() implements RouterEvent {
    @Override
    public String getName() {
        return "heartbeat";
    }
}
