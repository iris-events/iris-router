package id.global.core.router.ws;

import javax.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQClient;

@QuarkusTest
@Disabled
public class WebsocketQueueTests {

    @Inject
    RabbitMQClient client;

    @Test
    public void testDelivery() throws Exception {
        client.basicPublish("user", "#.say-hello", null, Buffer.buffer("test"));
        //client.basicPublish("websocket", "#.websocket", null, Buffer.buffer("test"));
    }

}
