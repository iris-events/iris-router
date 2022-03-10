package id.global.core.router.consumer;

import static id.global.common.constants.iris.MessagingHeaders.Message.EVENT_TYPE;

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

    //protected Channel channel;

    public void onApplicationStart(@Observes StartupEvent event) {
        createChanel();
        createQueues(getQueueName());

    }

    private void createChanel() {
        /*
         * try {
         * Connection connection = rabbitMQClient.connect(getNameSuffix(getQueueName(), "1.0"));
         * // create a channel
         * channel = connection.createChannel();
         * } catch (IOException e) {
         * throw new UncheckedIOException(e);
         * }
         */
    }

    protected void createQueues(String queueName) {

    }

    protected abstract String getQueueName();

    protected abstract ResponseMessageType getSocketMessageType();

    protected abstract List<String> getQueueRoles();

    protected String getNameSuffix(String name, String version) {
        StringBuilder stringBuffer = new StringBuilder()
                .append("router")
                .append(".")
                .append(name);

        if (version != null) {
            stringBuffer.append(".").append(version);
        }

        return stringBuffer.toString();
    }

    protected void setListener(String queueName) {
        setListener(queueName, 1, 0);
    }

    protected void setListener(String queueName, int concurrentConsumer, long sleep) {
        var options = new QueueOptions();
        options.setAutoAck(true);
        options.setMaxInternalQueueSize(50);
        client.start()
                .onSuccess(v -> {
                    // At this point the exchange, queue and binding will have been declared even if the client connects to a new server
                    client.basicConsumer(queueName, options, result -> {
                        if (result.succeeded()) {
                            RabbitMQConsumer mqConsumer = result.result();
                            mqConsumer.handler(this::handleMessage);
                            log.info("consumer started on '{}' --> {} --> {}", getQueueName(), getSocketMessageType(),
                                    getQueueRoles());
                        } else {
                            log.error("error", result.cause());

                        }

                    });
                })
                .onFailure(ex -> {
                    log.error("It went wrong: ", ex);
                });

        /*
         * try {
         * channel.basicConsume(queueName, true, new DefaultConsumer(channel) {
         *
         * @Override
         * public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
         * byte[] body) {
         * // just print the received message.
         * log.info("exchange: {}, queue: {}, routing key: {}, deliveryTag: {}", envelope.getExchange(), queueName,
         * envelope.getRoutingKey(), envelope.getDeliveryTag());
         * log.info("properties: {}", properties);
         * Object event = properties.getHeaders().get(EVENT_TYPE);
         * if (event == null) {
         * throw new RuntimeException("Required header '" + EVENT_TYPE + "' missing on message");
         * }
         *
         * AmpqMessage m = new AmpqMessage(body, properties, event.toString());
         * log.info("Received: consumerTag: {}, body: {}", consumerTag, new String(body, StandardCharsets.UTF_8));
         * try {
         * onMessage(m);
         * } catch (Exception e) {
         * log.warn("Error handling message", e);
         * }
         *
         * }
         * });
         * } catch (IOException e) {
         * throw new UncheckedIOException(e);
         * }
         * log.info("consumer started on '{}' --> {} --> {}", getQueueName(), getSocketMessageType(), getQueueRoles());
         */

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

    protected String getThreadPrefix() {
        return getClass().getSimpleName() + "-";
    }

    protected String getDeadPrefix(String name) {
        return "dead." + name;
    }

    public abstract void onMessage(AmqpMessage message);
}
