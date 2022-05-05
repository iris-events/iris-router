package id.global.core.router.service;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.common.annotations.iris.Message;
import id.global.common.constants.iris.Exchanges;
import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.iris.amqp.parsers.ExchangeParser;
import io.quarkus.runtime.StartupEvent;
import io.vertx.rabbitmq.RabbitMQClient;

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

    public void sendToBackend(String eventType, AmqpMessage message) {
        websocketRegistry.registerRequest(message);

        log.info("publishing message to '{}' - {}", FRONTEND_EXCHANGE, eventType);
        client.basicPublish(FRONTEND_EXCHANGE, eventType, message.properties(), message.body());
    }

    public void sendIrisEventToBackend(final UserSession userSession, final String clientTraceId, final Object message) {
        final var messageAnnotation = message.getClass().getAnnotation(Message.class);
        final var name = ExchangeParser.getFromAnnotationClass(messageAnnotation);
        final var msg = new RequestWrapper(name, clientTraceId, objectMapper.valueToTree(message));

        final var amqpMessage = userSession.createBackendRequest(msg);

        sendToBackend(name, amqpMessage);
    }
}
