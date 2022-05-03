package id.global.core.router.events;

import id.global.common.error.iris.ErrorType;
import id.global.common.error.iris.MessagingError;

public record ErrorEvent(ErrorType errorType, String code, String message) implements RouterEvent {

    public static final String NAME = "error";

    public static ErrorEvent of(MessagingError error, String message) {
        return new ErrorEvent(error.getType(), error.getClientCode(), message);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
