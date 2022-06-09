package id.global.core.router.ws;

import javax.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import id.global.iris.common.constants.Exchanges;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQClient;

@QuarkusTest
@Disabled
public class WebsocketQueueTests {

    @Inject
    RabbitMQClient client;

    @Test
    public void testDelivery() {
        client.start(asyncResult -> {
            if (asyncResult.succeeded()) {
                client.basicPublish(Exchanges.USER.getValue(), "say-hello.user", null, Buffer.buffer("test"));
            } else {
                throw new RuntimeException(asyncResult.cause());
            }
        });
    }

}
