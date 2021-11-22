package id.global.core.router.ws;

import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Connection;

import io.quarkiverse.rabbitmqclient.RabbitMQClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled
public class WebsocketQueueTests {

    @Inject
    RabbitMQClient rabbitMQClient;

    @Test
    public void testDelivery() throws Exception {
        Connection connection = rabbitMQClient.connect("test");
        var channel = connection.createChannel();
        channel.basicPublish("websocket", "#.websocket", null, "test".getBytes(StandardCharsets.UTF_8));
        channel.basicPublish("websocket", "#.websocket", null, "test".getBytes(StandardCharsets.UTF_8));
    }

}
