package id.global.core.router.consumer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.BuiltinExchangeType;

import id.global.core.router.model.AmpqMessage;
import id.global.core.router.model.ResponseHandler;
import id.global.core.router.model.ResponseMessageType;
import id.global.core.router.service.RequestRegistry;
import id.global.core.router.service.WebsocketRegistry;

/**
 * @author Tomaz Cerar
 */
public abstract class AbstractWebSocketConsumer extends BaseConsumer {

    private static final Logger log = LoggerFactory.getLogger(AbstractWebSocketConsumer.class);

    //todo use instance id
    public static final String routerId = UUID.randomUUID().toString().substring(0, 8);

    @Inject
    WebsocketRegistry websocketRegistry;

    @Inject
    RequestRegistry requestRegistry;

    public AbstractWebSocketConsumer() {
    }

    static String trimMessage(String message) {
        if (message.length() > 300) {
            return message.substring(0, 300) + " ...";
        }
        return message;
    }

    @Override
    protected void createQueues(String queueName) {

        final String queueWithSuffix = getNameSuffix(queueName, routerId);
        //ConsumerForMessage consumerForMessage = getConsumerForMessage();

        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 5000);

        //super.prefetchCount = consumerForMessage.consumerPrefetch();

        try {

            // declare exchanges and queues
            channel.queueDeclare(queueWithSuffix, true, false, true, args);
            channel.exchangeDeclare(queueName, BuiltinExchangeType.TOPIC, true, false, null);
            queueBind(queueWithSuffix, queueName, "#." + queueName);
            queueBind(queueWithSuffix, queueName, getRoutingKey(queueName));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        setListener(queueWithSuffix);
    }

    private void queueBind(String queue, String exchange, String routingKey) throws IOException {
        channel.queueBind(queue, exchange, routingKey);
        log.info("binding: '{}' --> '{}' with routing key: '{}'", queue, exchange, routingKey);
    }

    protected String getRoutingKey(String queueName) {
        return "#." + queueName + "." + routerId;
    }

    //non rpc message, handle on websocket
    protected void sendToSocket(AmpqMessage message) {
        final var handlers = getResponseHandlers(message);
        final var responseMessageType = getSocketMessageType();
        handlers.forEach(handler -> handler.handle(responseMessageType, message));
    }

    private List<ResponseHandler> getResponseHandlers(AmpqMessage message) {
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
            log.trace("Got message that we cannot find response handler for: sessionId: {}, userId: {}, event: {}",
                    sessionId, userId, message.eventType());
            handlers.add(websocketRegistry.getResponseHandler());
        }
        return handlers;
    }

    @Override
    public void onMessage(AmpqMessage message) {
        log.info("got websocket message: {}", message);

        final String router = message.routerId();
        /*
         * boolean valid = correlationId == null;
         *
         * if (!valid && !apiService.isRequestValid(correlationId) && ! websocketRegistry.isRequestValid(correlationId)) {
         * LOGGER.info("should be dropping msg: {}, event: {}", correlationId, event);
         * return;
         * }
         */
        final String correlationId = message.correlationId();
        final String messageBody = new String(message.body(), StandardCharsets.UTF_8);
        if (router != null) {
            if (!routerId.equals(router)) {
                log.info("Message trace id '{}' does not belong to this server, message: {}", correlationId,
                        trimMessage(messageBody));
                return;
            }
        }
        final String eventType = message.eventType();
        final String userId = message.userId();
        final String sessionId = message.sessionId();
        final String currentServiceId = message.currentServiceId();
        if (log.isTraceEnabled()) {
            log.trace("event: {}, userId: {}, session: {}, source: {} body: {}", eventType, userId, sessionId, currentServiceId,
                    messageBody);
        }

        if (requestRegistry.isRequestValid(correlationId)) {
            requestRegistry.publishResponse(getSocketMessageType(), message);
        } else {
            if (!"".equals(userId) && sessionId == null
                    && correlationId != null) { //correlationId check is for subscription messages
                log.warn("Message without userId and no sessionId, microservice: {}, message: {}", currentServiceId,
                        trimMessage(messageBody));
                return;
            }
            sendToSocket(message);
        }

    }
}
