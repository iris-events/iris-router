package id.global.core.router.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Tomaz Cerar
 */
public abstract class DefaultResponseHandler implements ResponseHandler {
    static Logger log = LoggerFactory.getLogger(DefaultResponseHandler.class);

    protected abstract ObjectMapper getObjectMapper();

    @Override
    public void handle(ResponseMessageType responseMessageType, AmqpMessage message) {
        if (responseMessageType != ResponseMessageType.ERROR) {
            onSuccess(responseMessageType, message);
        } else {
            log.warn("Handling exception error message.");
            onFailure(message);
        }
    }

    protected abstract void onFailure(final AmqpMessage message);

    protected abstract void onSuccess(ResponseMessageType responseMessageType, AmqpMessage message);

    /*
     * protected abstract void onError(ResponseMessageType responseMessageType, String requestId, String userId, String
     * sessionId, String event, String filter, BusinessExceptionEvent exceptionEvent);
     */
}
