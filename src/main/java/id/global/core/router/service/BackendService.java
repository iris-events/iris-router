package id.global.core.router.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;
import id.global.iris.common.constants.Exchanges;
import id.global.iris.parsers.ExchangeParser;
import io.quarkus.runtime.StartupEvent;
import io.vertx.rabbitmq.RabbitMQClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * @author Tomaz Cerar
 */
@ApplicationScoped
public class BackendService {
    private static final Logger log = LoggerFactory.getLogger(BackendService.class);
    private static final String FRONTEND_EXCHANGE = Exchanges.FRONTEND.getValue();

    private final WebsocketRegistry websocketRegistry;

    @Inject
    RabbitMQClient client;

    @Inject
    ObjectMapper objectMapper;

    public BackendService(WebsocketRegistry websocketRegistry) {
        this.websocketRegistry = websocketRegistry;

    }

    void createChanel(@Observes StartupEvent event) {
        client.start(asyncResult -> {
            if (asyncResult.succeeded()) {
                log.info("RabbitMQ successfully connected!");
            } else {
                log.info("Fail to connect to RabbitMQ {}", asyncResult.cause().getMessage());
            }
        });
    }

    private void sendToBackend(Scope scope, AmqpMessage message) {
        String eventType = message.eventType();
        websocketRegistry.registerRequest(message);
        if (scope == Scope.FRONTEND) {
            log.info("Publishing front message to backend '{}' - {}", FRONTEND_EXCHANGE, eventType);
            client.basicPublish(FRONTEND_EXCHANGE, eventType, message.properties(), message.body());
        } else if (scope == Scope.INTERNAL) {
            log.info("publishing internal message to '{}' ", eventType);
            client.basicPublish(eventType, "", message.properties(), message.body());
        } else {
            throw new RuntimeException("Cannot send message of scope " + scope);
        }
    }

    public void sendFrontendEvent(UserSession userSession, RequestWrapper requestWrapper) {
        final var message = userSession.createBackendRequest(requestWrapper);
        sendToBackend(Scope.FRONTEND, message);
    }

    public void sendInternalEvent(final UserSession userSession, final String clientTraceId, final Object message) {
        final var messageAnnotation = message.getClass().getAnnotation(Message.class);
        final var name = ExchangeParser.getFromAnnotationClass(messageAnnotation);
        final var msg = new RequestWrapper(name, clientTraceId, objectMapper.valueToTree(message));
        final var amqpMessage = userSession.createBackendRequest(msg);

        sendToBackend(Scope.INTERNAL, amqpMessage);
    }
}
