package id.global.core.router.ws;

import static id.global.iris.common.constants.MessagingHeaders.Message.EVENT_TYPE;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;

import id.global.iris.common.constants.Exchanges;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQClient;
import jakarta.inject.Inject;

@QuarkusTest
@Disabled
public class WebsocketQueueTests {

    @Inject
    RabbitMQClient client;

    @Test
    public void testDelivery() {
        client.start(asyncResult -> {
            if (asyncResult.succeeded()) {
                final var basicProperties = new AMQP.BasicProperties().builder()
                        .correlationId(UUID.randomUUID().toString())
                        .headers(Map.of(EVENT_TYPE, "hello"))
                        .build();
                client.basicPublish(Exchanges.USER.getValue(), "say-hello.user", basicProperties, Buffer.buffer("test"));
            } else {
                throw new RuntimeException(asyncResult.cause());
            }
        });
    }

}
