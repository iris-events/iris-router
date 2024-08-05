package org.iris_events.router.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.Json;
import org.iris_events.router.consumer.BaseConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

/**
 * @author Tomaz Cerar
 */
public abstract class DefaultResponseHandler implements ResponseHandler {
    static Logger log = LoggerFactory.getLogger(DefaultResponseHandler.class);

    protected abstract ObjectMapper getObjectMapper();

    @Override
    public void handle(ResponseMessageType responseMessageType, AmqpMessage message) {
        try {
            BaseConsumer.enrichMDC(message);
            if (responseMessageType != ResponseMessageType.ERROR) {
                onSuccess(responseMessageType, message);
            } else {
                try {
                    final var errorMessage = Json.decodeValue(message.body(), ErrorMessage.class);
                    log.info("Handling exception error message. {}/{}/{}/{}", message.currentServiceId(), message.originEventType(), errorMessage.errorType(), errorMessage.code());
                } catch (Exception e) {
                    log.info("Handling exception error message. {}/{}/{}", message.currentServiceId(), message.originEventType(), message.eventType());
                }
                onFailure(message);
            }
        } finally {
            MDC.clear();
        }
    }

    protected abstract void onFailure(final AmqpMessage message);

    protected abstract void onSuccess(ResponseMessageType responseMessageType, AmqpMessage message);

    public record ErrorMessage(@JsonProperty("error_type") String errorType, String code) {
    }
}
