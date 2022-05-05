package id.global.core.router.ws.message.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;

@QuarkusTest
class DefaultMessageHandlerTest {

    @Inject
    @DefaultHandler
    DefaultMessageHandler messageHandler;

    @InjectMock
    BackendService backendService;

    @Test
    void handle() {
        final var userSession = mock(UserSession.class);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), null);

        messageHandler.handle(userSession, requestWrapper);

        verify(backendService).sendIrisEventToBackend(userSession, requestWrapper.clientTraceId(), requestWrapper);
    }
}