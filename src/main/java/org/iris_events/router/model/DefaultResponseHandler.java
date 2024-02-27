package org.iris_events.router.model;

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
                log.info("Handling exception error message. {}/{}", message.originServiceId(), message.eventType());
                onFailure(message);
            }
        } finally {
            MDC.clear();
        }
    }

    protected abstract void onFailure(final AmqpMessage message);

    protected abstract void onSuccess(ResponseMessageType responseMessageType, AmqpMessage message);


}
