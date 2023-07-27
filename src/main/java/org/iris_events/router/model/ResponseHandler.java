package org.iris_events.router.model;

/**
 * @author Tomaz Cerar
 */
public interface ResponseHandler {
    void handle(ResponseMessageType responseMessageType, AmqpMessage message);
}
