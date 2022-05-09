package id.global.core.router.events;

import id.global.common.iris.constants.Exchanges;
import id.global.common.iris.error.ErrorType;
import id.global.common.iris.error.MessagingError;

public record ErrorEvent(ErrorType errorType, String code, String message) implements RouterEvent {

    public static final String NAME = Exchanges.ERROR.getValue();

    public static ErrorEvent of(MessagingError error, String message) {
        return new ErrorEvent(error.getType(), error.getClientCode(), message);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
