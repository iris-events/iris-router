package id.global.core.router.events;

public record ErrorEvent(ErrorType errorType, ErrorCode code, String message) implements RouterEvent {
    @Override
    public String getName() {
        return "error";
    }
}
