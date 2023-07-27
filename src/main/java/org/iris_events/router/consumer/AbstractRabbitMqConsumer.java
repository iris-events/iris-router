package org.iris_events.router.consumer;

import jakarta.inject.Inject;
import org.iris_events.router.model.AmqpMessage;
import org.iris_events.router.model.ResponseHandler;
import org.iris_events.router.model.ResponseMessageType;
import org.iris_events.router.service.RequestRegistry;
import org.iris_events.router.service.RouterIdProvider;
import org.iris_events.router.service.WebsocketRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tomaz Cerar
 */
public abstract class AbstractRabbitMqConsumer extends BaseConsumer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    //@ConfigProperty(name = "quarkus.uuid")
    String routerId = RouterIdProvider.routerId();

    @Inject
    WebsocketRegistry websocketRegistry;

    @Inject
    RequestRegistry requestRegistry;

    public AbstractRabbitMqConsumer() {
    }

    static String trimMessage(String message) {
        if (message.length() > 300) {
            return message.substring(0, 300) + " ...";
        }
        return message;
    }


    //non rpc message, handle on websocket
    protected void sendToSocket(AmqpMessage message) {
        final var handlers = getResponseHandlers(message);
        final var responseMessageType = getSocketMessageType();
        handlers.forEach(handler -> handler.handle(responseMessageType, message));
    }

    private List<ResponseHandler> getResponseHandlers(AmqpMessage message) {
        List<ResponseHandler> handlers = new ArrayList<>();
        if (getSocketMessageType() == ResponseMessageType.BROADCAST) {
            handlers.add(websocketRegistry.getResponseHandler());
        }
        String userId = message.userId();
        String sessionId = message.sessionId();
        if (handlers.isEmpty() && sessionId != null) {
            if (websocketRegistry.getSession(sessionId) != null) {
                handlers.add(websocketRegistry.getResponseHandler());
            }
        }
        if (handlers.isEmpty() && userId != null) {
            if (websocketRegistry.hasUserSession(userId)) {
                handlers.add(websocketRegistry.getResponseHandler());
            }
        }
        if (handlers.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.warn("Got message that we cannot find response handler for: sessionId: {}, userId: {}, event: {}",
                        sessionId, userId, message.eventType());
            }
            handlers.add(websocketRegistry.getResponseHandler());
        }
        return handlers;
    }

    @Override
    public void onMessage(AmqpMessage message) {
        log.debug("Got message from backend. Looking for websocket session to forward it.");
        final String router = message.routerId();

        final String correlationId = message.correlationId();

        final String messageBody = message.body().copy().toString(StandardCharsets.UTF_8);
        if (router != null) {
            if (!routerId.equals(router)) {
                log.debug("Message correlation id does not belong to this server. Discarding message.");
                return;
            }
        }

        final String currentServiceId = message.currentServiceId();
        if (log.isTraceEnabled()) {
            log.info("Message source: {} body: {}", currentServiceId, messageBody);
        }

        if (requestRegistry.isRequestValid(correlationId)) {
            requestRegistry.publishResponse(getSocketMessageType(), message);
        } else {
            // TODO: consider introducing custom header for subscription messages and re-enabling this check
            //            if (!"".equals(userId) && sessionId == null
            //                    && correlationId != null) { //correlationId check is for subscription messages
            //                log.warn("Message without userId and no sessionId, microservice: {}, message: {}", currentServiceId,
            //                        trimMessage(messageBody));
            //                return;
            //            }
            sendToSocket(message);
        }
    }
}
