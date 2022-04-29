package id.global.core.router.events;

public record ErrorEvent(ErrorType errorType, ErrorCode code, String message) implements RouterEvent {

    public static final String NAME = "error";

    @Override
    public String getName() {
        return NAME;
    }
}
