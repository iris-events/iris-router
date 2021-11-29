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

import com.rabbitmq.client.AMQP;
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
    protected void sendToSocket(String userId, String sessionId, String dataType, String correlationId, String message) {
        final var handlers = getResponseHandlers(sessionId, userId, dataType);
        final var responseMessageType = getSocketMessageType();
        handlers.forEach(handler -> handler.handle(responseMessageType, correlationId, userId, sessionId, dataType, message));
    }

    private List<ResponseHandler> getResponseHandlers(String sessionId, String userId, String dataType) {
        List<ResponseHandler> handlers = new ArrayList<>();
        if (getSocketMessageType() == ResponseMessageType.BROADCAST) {
            handlers.add(websocketRegistry.getResponseHandler());
        }
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
                    sessionId, userId, dataType);
            handlers.add(websocketRegistry.getResponseHandler());
        }
        return handlers;
    }

    private String getStringHeader(AMQP.BasicProperties props, String name) {
        var r = props.getHeaders().get(name);
        if (r != null) {
            return r.toString();
        }
        return null;
    }

    @Override
    public void onMessage(AmpqMessage message) {
        log.info("got websocket message: {}", message);

        var headers = message.properties().getHeaders();
        final String router = getStringHeader(message.properties(), "router");
        /*
         * boolean valid = correlationId == null;
         *
         * if (!valid && !apiService.isRequestValid(correlationId) && ! websocketRegistry.isRequestValid(correlationId)) {
         * LOGGER.info("should be dropping msg: {}, event: {}", correlationId, event);
         * return;
         * }
         */
        final String messageBody = new String(message.body(), StandardCharsets.UTF_8);
        //final String correlationId = message.properties().getCorrelationId();
        final String correlationId = getStringHeader(message.properties(), "traceId");
        if (router != null) {
            if (!routerId.equals(router)) {
                log.info("Message trace id '{}' does not belong to this server, message: {}", correlationId,
                        trimMessage(messageBody));
                return;
            }
        }

        final String eventType = getStringHeader(message.properties(), "eventType");
        final String traceId = getStringHeader(message.properties(), "traceId");
        final String clientTraceId = (String) headers.get("clientTraceId");
        final String userId = getStringHeader(message.properties(), "userId");
        final String sessionId = getStringHeader(message.properties(), "sessionId");
        final String currentServiceId = getStringHeader(message.properties(), "currentServiceId");
        //final String ipAddress = (String) headers.get("ipAddress");

        log.trace("event: {}, userId: {}, session: {}, source: {} body: {}", eventType, userId, sessionId, currentServiceId,
                messageBody);

        if (requestRegistry.isRequestValid(correlationId)) {
            requestRegistry.publishResponse(getSocketMessageType(), correlationId, clientTraceId, userId, sessionId, eventType,
                    messageBody);
        } else {
            if (!"".equals(userId) && sessionId == null
                    && correlationId != null) { //correlationId check is for subscription messages
                log.warn("Message without userId and no sessionId, microservice: {}, message: {}", currentServiceId,
                        trimMessage(messageBody));
                return;
            }
            sendToSocket(userId, sessionId, eventType, correlationId, messageBody);
        }

    }
}
