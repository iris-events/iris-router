package id.global.core.router.ws;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import id.global.common.annotations.iris.Message;
import id.global.core.router.model.RequestWrapper;
import id.global.iris.irisdemoinventory.InventoryStockInquiry;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest

public class WebsocketTest {

    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();

    @TestHTTPResource("/v0/websocket")
    URI uri;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSimpleMessage() throws Exception {
        try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            Assertions.assertEquals("CONNECT", MESSAGES.poll(10, TimeUnit.SECONDS));
            /*
             * //todo once we have properly build types
             * ConsentEvent consentEvent = new ConsentEvent();
             * consentEvent.setAcrcId("some acrc");
             * send(session, consentEvent);
             */
            var stockInquiry = new InventoryStockInquiry();

            send(session, stockInquiry);
            /*
             * Assertions.assertEquals("User stu joined", MESSAGES.poll(10, TimeUnit.SECONDS));
             * session.getAsyncRemote().sendText("hello world");
             * Assertions.assertEquals(">> stu: hello world", MESSAGES.poll(10, TimeUnit.SECONDS));
             */
        }
    }

    private void send(Session session, Object event) throws Exception {
        Message message = event.getClass().getAnnotation(Message.class);

        var payload = objectMapper.convertValue(event, ObjectNode.class);

        RequestWrapper requestMessage = new RequestWrapper(
                message.name(),
                UUID.randomUUID().toString(),
                payload);
        session
                .getAsyncRemote()
                .sendText(objectMapper.writeValueAsString(requestMessage));

    }

    @ClientEndpoint
    public static class Client {
        @Inject
        ObjectMapper objectMapper;

        @OnOpen
        public void open(Session session) {
            MESSAGES.add("CONNECT");
            // Send a message to indicate that we are ready,
            // as the message handler may not be registered immediately after this callback.

        }

        @OnMessage
        void message(String msg) {
            MESSAGES.add(msg);
        }

    }

}
