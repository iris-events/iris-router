package id.global.core.router.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;

import id.global.common.constants.iris.Exchanges;
import id.global.core.router.model.AmqpMessage;
import io.quarkiverse.rabbitmqclient.RabbitMQClient;
import io.quarkus.runtime.StartupEvent;

/**
 * @author Tomaz Cerar
 */
@ApplicationScoped
public class BackendService {
    private static final Logger log = LoggerFactory.getLogger(BackendService.class);
    private static final String FRONTEND_EXCHANGE = Exchanges.FRONTEND.getValue();

    @Inject
    RabbitMQClient rabbitMQClient;

    private final WebsocketRegistry websocketRegistry;
    private Channel channel;

    public BackendService(WebsocketRegistry websocketRegistry) {
        this.websocketRegistry = websocketRegistry;

    }

    void createChanel(@Observes StartupEvent event) {
        try {
            Connection connection = rabbitMQClient.connect("backend publisher");

            // create a channel
            channel = connection.createChannel();
            channel.addConfirmListener(new ConfirmListener() {
                @Override
                public void handleAck(long deliveryTag, boolean multiple) {
                    log.info("ack");
                }

                @Override
                public void handleNack(long deliveryTag, boolean multiple) {
                    log.info("nack");
                }
            });
            channel.addReturnListener((replyCode, replyText, exchange, rk, properties, body) -> {
                log.info("reply code {}, reply text {}, exchange {}, rk {}", replyCode, replyText, exchange, rk);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void sendToBackend(String eventType, AmqpMessage message) {
        websocketRegistry.registerRequest(message);

        try {
            log.info("publishing message to '{}' - {}", FRONTEND_EXCHANGE, eventType);
            channel.basicPublish(FRONTEND_EXCHANGE, eventType, message.properties(), message.body());
        } catch (IOException e) {
            log.error("Could not send message", e);
        }
    }
}
