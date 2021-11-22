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
    public void handle(ResponseMessageType responseMessageType, String correlationId, String userId, String sessionId,
            String eventType, String message) {
        if (message == null) {
            LOGGER.warn("Got message with null body for type: {}", eventType);
            return;
        }
        if (!"exception".equals(eventType)) {
            onSuccess(responseMessageType, correlationId, userId, sessionId, eventType, message);
        } else {
            LOGGER.warn("No exception handling yet");
        }
    }

    protected abstract void onSuccess(ResponseMessageType responseMessageType, String correlationId, String userId,
            String sessionId, String eventType, String message);

    /*
     * protected abstract void onError(ResponseMessageType responseMessageType, String requestId, String userId, String
     * sessionId, String event, String filter, BusinessExceptionEvent exceptionEvent);
     */
}
