package id.global.core.router.events;

import id.global.iris.common.constants.Exchanges;
import id.global.iris.common.error.ErrorType;

public record ErrorEvent(ErrorType errorType, String code, String message) implements RouterEvent {

    public static final String AUTHORIZATION_FAILED_CLIENT_CODE = "AUTHORIZATION_FAILED";
    public static final String EVENT_MISSING_CLIENT_CODE = "EVENT_MISSING";
    public static final String PAYLOAD_MISSING_CLIENT_CODE = "PAYLOAD_MISSING";
    public static final String TOKEN_EXPIRED_CLIENT_CODE = "TOKEN_EXPIRED";
    public static final String UNAUTHORIZED_CLIENT_CODE = "UNAUTHORIZED";

    public static final String NAME = Exchanges.ERROR.getValue();

    @Override
    public String getName() {
        return NAME;
    }
}
