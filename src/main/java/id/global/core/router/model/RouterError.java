package id.global.core.router.model;

import id.global.common.iris.error.ErrorType;
import id.global.common.iris.error.MessagingError;

public enum RouterError implements MessagingError {
    TOKEN_EXPIRED(ErrorType.UNAUTHORIZED);

    private final ErrorType errorType;

    RouterError(final ErrorType errorType) {
        this.errorType = errorType;
    }

    @Override
    public ErrorType getType() {
        return errorType;
    }

    @Override
    public String getClientCode() {
        return this.name();
    }
}
