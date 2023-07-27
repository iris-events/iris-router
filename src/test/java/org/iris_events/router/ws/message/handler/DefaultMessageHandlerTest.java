package org.iris_events.router.ws.message.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.iris_events.router.service.BackendService;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.iris_events.router.model.AmqpMessage;
import org.iris_events.router.model.RequestWrapper;
import org.iris_events.router.model.UserSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;

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
