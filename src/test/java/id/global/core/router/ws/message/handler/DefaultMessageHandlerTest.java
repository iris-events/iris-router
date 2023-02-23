package id.global.core.router.ws.message.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.vertx.core.buffer.Buffer;

@QuarkusTest
class DefaultMessageHandlerTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @DefaultHandler
    DefaultMessageHandler messageHandler;

    @InjectMock
    BackendService backendService;

    @Test
    void handle() throws Exception {
        final var userSession = mock(UserSession.class);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), null);
        final var amqpMessage = new AmqpMessage(Buffer.buffer(objectMapper.writeValueAsBytes(requestWrapper.payload())), null,
                requestWrapper.event());
        when(userSession.isValid()).thenReturn(true);
        when(userSession.createBackendRequest(requestWrapper)).thenReturn(amqpMessage);

        messageHandler.handle(userSession, requestWrapper);

        verify(backendService).sendFrontendEvent(userSession, requestWrapper);
    }
}
