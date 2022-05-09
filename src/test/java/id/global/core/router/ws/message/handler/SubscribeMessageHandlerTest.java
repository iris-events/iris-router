package id.global.core.router.ws.message.handler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.common.iris.error.SecurityError;
import id.global.core.router.events.ErrorEvent;
import id.global.core.router.events.UserAuthenticatedEvent;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.Subscribe;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;

@QuarkusTest
class SubscribeMessageHandlerTest {

    @Inject
    @Named(SubscribeMessageHandler.EVENT_NAME)
    SubscribeMessageHandler messageHandler;

    @InjectMock
    BackendService backendService;

    @InjectMock
    WebsocketRegistry websocketRegistry;

    @Inject
    ObjectMapper objectMapper;

    private UserSession userSession;

    @BeforeEach
    void beforeEach() {
        userSession = mock(UserSession.class);
    }

    @Test
    void loginFailed() {
        final var token = UUID.randomUUID().toString();
        final var subscribe = new Subscribe(null, token, null);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), objectMapper.valueToTree(subscribe));

        when(websocketRegistry.login(userSession, token)).thenReturn(false);

        messageHandler.handle(userSession, requestWrapper);

        final var errorEventArgumentCaptor = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(userSession).sendEvent(errorEventArgumentCaptor.capture(), eq(requestWrapper.clientTraceId()));

        final var errorEvent = errorEventArgumentCaptor.getValue();
        assertThat(errorEvent.getName(), is(ErrorEvent.NAME));
        assertThat(errorEvent.errorType(), is(SecurityError.AUTHORIZATION_FAILED.getType()));
        assertThat(errorEvent.code(), is(SecurityError.AUTHORIZATION_FAILED.getClientCode()));
        assertThat(errorEvent.message(), is("authorization failed"));
    }

    @Test
    void login() {
        final var token = UUID.randomUUID().toString();
        final var subscribe = new Subscribe(null, token, null);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), objectMapper.valueToTree(subscribe));

        when(websocketRegistry.login(userSession, token)).thenReturn(true);

        messageHandler.handle(userSession, requestWrapper);

        final var userAuthenticatedEventArgumentCaptor = ArgumentCaptor.forClass(UserAuthenticatedEvent.class);
        verify(userSession).sendEvent(userAuthenticatedEventArgumentCaptor.capture(), eq(requestWrapper.clientTraceId()));

        final var userAuthenticatedEvent = userAuthenticatedEventArgumentCaptor.getValue();
        assertThat(userAuthenticatedEvent.getName(), is(UserAuthenticatedEvent.NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void setSendHeartbeat(boolean heartbeat) {
        final var subscribe = new Subscribe(null, null, heartbeat);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), objectMapper.valueToTree(subscribe));

        messageHandler.handle(userSession, requestWrapper);

        verify(userSession).setSendHeartbeat(heartbeat);
    }

    @Test
    void emptySubscriptions() {
        final var subscribe = new Subscribe(null, null, null);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), objectMapper.valueToTree(subscribe));

        messageHandler.handle(userSession, requestWrapper);

        verifyNoInteractions(backendService);
    }

    @Test
    void subscriptions() {
        final var resources = objectMapper.createArrayNode();
        final var subscribe = new Subscribe(resources, null, null);
        final var requestWrapper = new RequestWrapper(null, UUID.randomUUID().toString(), objectMapper.valueToTree(subscribe));

        messageHandler.handle(userSession, requestWrapper);

        verify(backendService).sendIrisEventToBackend(userSession, requestWrapper.clientTraceId(),
                new id.global.iris.irissubscription.Subscribe(resources));
    }
}
