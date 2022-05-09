package id.global.core.router.consumer;

import static id.global.common.iris.constants.MessagingHeaders.Message.EVENT_TYPE;

import java.util.List;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.ResponseMessageType;
import io.quarkus.runtime.StartupEvent;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;

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
        return "router" + "." + name + "." + AbstractWebSocketConsumer.routerId;
    }

    protected void setListener(String queueName) {
        var options = new QueueOptions();
        options.setAutoAck(true);
        options.setMaxInternalQueueSize(50);

        client.basicConsumer(queueName, options, result -> {
            if (result.succeeded()) {
                RabbitMQConsumer mqConsumer = result.result();
                mqConsumer.handler(this::handleMessage);
                log.info("consumer started on '{}' --> {} --> {}", getQueueName(), getSocketMessageType(), getQueueRoles());
            } else {
                log.error("error", result.cause());
            }
        });
    }

    private void handleMessage(RabbitMQMessage message) {
        var envelope = message.envelope();
        var properties = message.properties();
        var body = message.body();

        // just print the received message.
        log.info("exchange: {}, routing key: {}, deliveryTag: {}", envelope.getExchange(), envelope.getRoutingKey(),
                envelope.getDeliveryTag());
        log.info("properties: {}", properties);
        Object event = properties.getHeaders().get(EVENT_TYPE);
        if (event == null) {
            throw new RuntimeException("Required header '" + EVENT_TYPE + "' missing on message");
        }

        AmqpMessage m = new AmqpMessage(body, properties, event.toString());
        log.info("Received: consumerTag: {}, body: {}", message.consumerTag(), body);
        try {
            onMessage(m);
        } catch (Exception e) {
            log.warn("Error handling message", e);
        }
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
