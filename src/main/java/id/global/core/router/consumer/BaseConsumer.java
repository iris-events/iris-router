package id.global.core.router.consumer;

import static id.global.common.headers.amqp.MessageHeaders.EVENT_TYPE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import id.global.core.router.model.AmpqMessage;
import id.global.core.router.model.ResponseMessageType;
import io.quarkiverse.rabbitmqclient.RabbitMQClient;
import io.quarkus.runtime.StartupEvent;

public abstract class BaseConsumer {

    private static final Logger log = LoggerFactory.getLogger(BaseConsumer.class);

    @Inject
    RabbitMQClient rabbitMQClient;

    protected Channel channel;

    public void onApplicationStart(@Observes StartupEvent event) {
        createChanel();
        createQueues(getQueueName());

    }

    private void createChanel() {
        try {
            Connection connection = rabbitMQClient.connect(getNameSuffix(getQueueName(), "1.0"));
            // create a channel
            channel = connection.createChannel();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    public void send(String message) {
        try {
            // send a message to the exchange
            channel.basicPublish("test", "#", null, message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected void setListener(String queueName) {
        setListener(queueName, 1, 0);
    }

    protected void setListener(String queueName, int concurrentConsumer, long sleep) {
        try {
            channel.basicConsume(queueName, true, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                        byte[] body) {
                    // just print the received message.
                    log.info("exchange: {}, queue: {}, routing key: {}, deliveryTag: {}", envelope.getExchange(), queueName,
                            envelope.getRoutingKey(), envelope.getDeliveryTag());
                    log.info("properties: {}", properties);
                    Object event = properties.getHeaders().get(EVENT_TYPE);
                    if (event == null) {
                        throw new RuntimeException("Required header '" + EVENT_TYPE + "' missing on message");
                    }

                    AmpqMessage m = new AmpqMessage(body, properties, event.toString());
                    log.info("Received: consumerTag: {}, body: {}", consumerTag, new String(body, StandardCharsets.UTF_8));
                    try {
                        onMessage(m);
                    } catch (Exception e) {
                        log.warn("Error handling message", e);
                    }

                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("consumer started on '{}' --> {} --> {}", getQueueName(), getSocketMessageType(), getQueueRoles());

    }

    protected String getThreadPrefix() {
        return getClass().getSimpleName() + "-";
    }

    protected String getDeadPrefix(String name) {
        return "dead." + name;
    }

    public abstract void onMessage(AmpqMessage message);
}
