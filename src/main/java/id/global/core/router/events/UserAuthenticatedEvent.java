package id.global.core.router.events;

public record UserAuthenticatedEvent() implements RouterEvent {
    @Override
    public String getName() {
        return "user-authenticated";
    }
}
