package id.global.core.router.consumer;

import static id.global.iris.common.constants.MessagingHeaders.Message.EVENT_TYPE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.ResponseMessageType;
import id.global.iris.common.constants.MDCProperties;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.providers.helpers.VertxContext;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

public abstract class BaseConsumer {

    private static final Logger log = LoggerFactory.getLogger(BaseConsumer.class);

    @Inject
    RabbitMQClient client;

    public void onApplicationStart(@Observes StartupEvent event) {
        createQueues(getQueueName());
        client.start(asyncResult -> {
            if (asyncResult.succeeded()) {
                log.info("RabbitMQ successfully connected for queue {}!", getQueueName());
            } else {
                final var rootCause = findRootCause(asyncResult.cause());
                final var message = rootCause != null ? rootCause.getMessage() : "N/A";
                log.error(String.format("Failed to connect to RabbitMQ for '%s' consumer. Cause: %s", getQueueName(), message),
                        rootCause);
            }
        });
    }

    protected abstract void createQueues(String queueName);

    protected abstract String getQueueName();

    protected abstract ResponseMessageType getSocketMessageType();

    protected abstract List<String> getQueueRoles();

    public abstract void onMessage(AmqpMessage message);

    protected String getNameSuffix(String name) {
        return "router" + "." + name + "." + AbstractRabbitMqConsumer.routerId;
    }

    protected void setListener(String queueName) {
        var options = new QueueOptions();
        options.setAutoAck(true);
        options.setMaxInternalQueueSize(50);

        client.basicConsumer(queueName, options, result -> {
            if (result.succeeded()) {
                RabbitMQConsumer mqConsumer = result.result();
                mqConsumer.handler(message -> {
                    final var newDuplicatedContext = VertxContext.createNewDuplicatedContext();
                    VertxContext.runOnContext(newDuplicatedContext, () -> handleMessage(message));
                });
                log.info("consumer started on '{}' --> {} --> {}", getQueueName(), getSocketMessageType(), getQueueRoles());
            } else {
                log.error("error", result.cause());
            }
        });
    }

    private void handleMessage(RabbitMQMessage message) {
        try {
            var properties = message.properties();
            var body = message.body();
            final var correlationId = properties.getCorrelationId();
            if (correlationId != null) {
                MDC.put(MDCProperties.CORRELATION_ID, correlationId);
            }

            Object event = properties.getHeaders().get(EVENT_TYPE);
            if (event == null) {
                throw new RuntimeException("Required header '" + EVENT_TYPE + "' missing on message");
            }

            AmqpMessage m = new AmqpMessage(body, properties, event.toString());
            enrichMDC(m);
            onMessage(m);
        } catch (Exception e) {
            log.warn("Error handling message", e);
        } finally {
            MDC.clear();
        }
    }

    private static void enrichMDC(final AmqpMessage m) {
        if (m.sessionId() != null)
            MDC.put(MDCProperties.SESSION_ID, m.sessionId());
        if (m.userId() != null)
            MDC.put(MDCProperties.USER_ID, m.userId());
        if (m.clientTraceId() != null)
            MDC.put(MDCProperties.CLIENT_TRACE_ID, m.clientTraceId());
        if (m.correlationId() != null)
            MDC.put(MDCProperties.CORRELATION_ID, m.correlationId());
        MDC.put(MDCProperties.EVENT_TYPE, m.eventType());
    }

    private static Throwable findRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
