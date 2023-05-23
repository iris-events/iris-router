package id.global.core.router.ws.message.handler;

import static id.global.core.router.events.ErrorEvent.AUTHORIZATION_FAILED_CLIENT_CODE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.events.ErrorEvent;
import id.global.core.router.events.UserAuthenticatedEvent;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.Subscribe;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;
import id.global.iris.common.error.ErrorType;
import id.global.iris.irissubscription.payload.Resource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.inject.Named;

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
        when(userSession.isValid()).thenReturn(true);
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
        assertThat(errorEvent.errorType(), is(ErrorType.AUTHENTICATION_FAILED));
        assertThat(errorEvent.code(), is(AUTHORIZATION_FAILED_CLIENT_CODE));
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
        final var resourceId = "resourceId";
        final var resourceType = "resourceType";

        final var resources = List.of(new Resource(resourceId, resourceType));

        final var subscribe = new Subscribe(resources, null, null);
        final var requestWrapper = new RequestWrapper("subscribe", UUID.randomUUID().toString(),
                objectMapper.valueToTree(subscribe));

        messageHandler.handle(userSession, requestWrapper);

        verify(backendService).sendInternalEvent(userSession, requestWrapper.clientTraceId(),
                new id.global.iris.irissubscription.SubscribeInternal(resourceId, resourceType));
    }
}
