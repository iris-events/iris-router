package id.global.core.router.consumer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.ResponseHandler;
import id.global.core.router.model.ResponseMessageType;
import id.global.core.router.service.RequestRegistry;
import id.global.core.router.service.WebsocketRegistry;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

/**
 * @author Tomaz Cerar
 */
public abstract class AbstractRabbitMqConsumer extends BaseConsumer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    //todo use instance id
    public static final String routerId = UUID.randomUUID().toString().substring(0, 8);

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

    @Override
    protected void createQueues(String queueName) {
        final String queueWithSuffix = getNameSuffix(queueName);
        final var args = new JsonObject();
        args.put("x-message-ttl", 5000);
        client.addConnectionEstablishedCallback(promise -> {
            client.queueDeclare(queueWithSuffix, true, false, true, args)
                    .compose(dok -> queueBind(queueWithSuffix, queueName, "#." + queueName))
                    .compose(dok -> queueBind(queueWithSuffix, queueName, getRoutingKey(queueName)))
                    .onSuccess(unused -> setListener(queueWithSuffix))
                    .onComplete(promise);
        });
    }

    private Future<Void> queueBind(String queue, String exchange, String routingKey) {
        log.info("binding: '{}' --> '{}' with routing key: '{}'", queue, exchange, routingKey);
        return client.queueBind(queue, exchange, routingKey);
    }

    protected String getRoutingKey(String queueName) {
        return "#." + queueName + "." + routerId;
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
            log.warn("Got message that we cannot find response handler for: sessionId: {}, userId: {}, event: {}",
                    sessionId, userId, message.eventType());
            handlers.add(websocketRegistry.getResponseHandler());
        }
        return handlers;
    }

    @Override
    public void onMessage(AmqpMessage message) {
        log.debug("Got message from backend. Looking for websocket session to forward it.");
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

        final String messageBody = message.body().copy().toString(StandardCharsets.UTF_8);
        if (router != null) {
            if (!routerId.equals(router)) {
                log.warn("Message correlation id does not belong to this server. Discarding message.");
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
