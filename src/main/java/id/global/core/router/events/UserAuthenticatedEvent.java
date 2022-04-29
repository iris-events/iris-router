package id.global.core.router.events;

public record UserAuthenticatedEvent() implements RouterEvent {

    public static final String NAME = "user-authenticated";

    @Override
    public String getName() {
        return NAME;
    }
}
