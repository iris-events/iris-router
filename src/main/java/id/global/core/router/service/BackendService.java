package id.global.core.router.service;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.common.constants.iris.Exchanges;
import id.global.core.router.model.AmqpMessage;
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

    public BackendService(WebsocketRegistry websocketRegistry) {
        this.websocketRegistry = websocketRegistry;

    }

    void createChanel(@Observes StartupEvent event) {
        try {

            /*
             * Connection connection = rabbitMQClient.connect("backend publisher");
             *
             * // create a channel
             * channel = connection.createChannel();
             * channel.addConfirmListener(new ConfirmListener() {
             *
             * @Override
             * public void handleAck(long deliveryTag, boolean multiple) {
             * log.info("ack");
             * }
             *
             * @Override
             * public void handleNack(long deliveryTag, boolean multiple) {
             * log.info("nack");
             * }
             * });
             * channel.addReturnListener((replyCode, replyText, exchange, rk, properties, body) -> {
             * log.info("reply code {}, reply text {}, exchange {}, rk {}", replyCode, replyText, exchange, rk);
             * });
             */

            client.start(asyncResult -> {
                if (asyncResult.succeeded()) {
                    log.info("RabbitMQ successfully connected!");
                } else {
                    log.info("Fail to connect to RabbitMQ {}", asyncResult.cause().getMessage());
                }
            });

            /*
             * client.addConnectionEstablishedCallback(promise -> {
             * client.exchangeDeclare("exchange", "fanout", true, false)
             * .compose(v -> {
             * return client.queueDeclare("queue", false, true, true);
             * })
             * .compose(declareOk -> {
             * return client.queueBind(declareOk.getQueue(), "exchange", "");
             * })
             * .onComplete(promise);
             * });
             */

            /*
             * channel.addReturnListener((replyCode, replyText, exchange, rk, properties, body) -> {
             * log.info("reply code {}, reply text {}, exchange {}, rk {}", replyCode, replyText, exchange, rk);
             * });
             */
        } catch (RuntimeException e) {
            throw e;
        }
    }

    public void sendToBackend(String eventType, AmqpMessage message) {
        websocketRegistry.registerRequest(message);

        log.info("publishing message to '{}' - {}", FRONTEND_EXCHANGE, eventType);
        client.basicPublish(FRONTEND_EXCHANGE, eventType, message.properties(), message.body());
    }
}
