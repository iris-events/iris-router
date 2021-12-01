package id.global.core.router.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Tomaz Cerar
 */
public abstract class DefaultResponseHandler implements ResponseHandler {
    static Logger LOGGER = LoggerFactory.getLogger(DefaultResponseHandler.class);

    protected abstract ObjectMapper getObjectMapper();

    @Override
    public void handle(ResponseMessageType responseMessageType, AmpqMessage message) {
        String eventType = message.eventType();
        if (!"exception".equals(eventType)) {
            onSuccess(responseMessageType, message);
        } else {
            LOGGER.warn("No exception handling yet");
        }
    }

    protected abstract void onSuccess(ResponseMessageType responseMessageType, AmpqMessage message);

    /*
     * protected abstract void onError(ResponseMessageType responseMessageType, String requestId, String userId, String
     * sessionId, String event, String filter, BusinessExceptionEvent exceptionEvent);
     */
}
