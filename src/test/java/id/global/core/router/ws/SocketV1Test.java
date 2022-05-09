package id.global.core.router.ws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.util.UUID;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.inject.Inject;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.common.iris.error.ClientError;
import id.global.core.router.events.ErrorEvent;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;
import id.global.core.router.ws.message.handler.DefaultHandler;
import id.global.core.router.ws.message.handler.MessageHandler;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SocketV1Test {

    SocketV1 socketV1;

    WebsocketRegistry websocketRegistry;

    BackendService backendService;

    Instance<MessageHandler> messageHandlers;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        socketV1 = new SocketV1();
        messageHandlers = mock(Instance.class);
        websocketRegistry = mock(WebsocketRegistry.class);
        backendService = mock(BackendService.class);
        socketV1.messageHandlers = messageHandlers;
        socketV1.websocketRegistry = websocketRegistry;
        socketV1.backendService = backendService;
        socketV1.objectMapper = objectMapper;
    }

    @Test
    void onMessageEmpty() {
        final var session = mock(Session.class);
        socketV1.onMessage(session, "");

        verifyNoInteractions(websocketRegistry);
        verifyNoInteractions(backendService);
    }

    @Test
    void onMessage() {
        final var sessionId = UUID.randomUUID().toString();
        final var session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);

        final var userSession = mock(UserSession.class);
        when(websocketRegistry.getSession(sessionId)).thenReturn(userSession);

        final Instance<MessageHandler> messageHandlerInstance = mock(Instance.class);
        final var messageHandler = mock(MessageHandler.class);
        when(messageHandlerInstance.get()).thenReturn(messageHandler);
        when(messageHandlers.select(any(Annotation.class))).thenReturn(messageHandlerInstance);

        socketV1.onMessage(session, "{}");

        final var errorEventArgumentCaptor = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(userSession, times(2)).sendEvent(errorEventArgumentCaptor.capture(), eq(null));

        final var errorEventArgumentCaptorAllValues = errorEventArgumentCaptor.getAllValues();
        final var eventMissingError = errorEventArgumentCaptorAllValues.get(0);
        assertThat(eventMissingError.getName(), is(ErrorEvent.NAME));
        assertThat(eventMissingError.errorType(), is(ClientError.BAD_REQUEST.getType()));
        assertThat(eventMissingError.code(), is(ClientError.BAD_REQUEST.getClientCode()));
        assertThat(eventMissingError.message(), is("'event' missing"));

        final var payloadMissingError = errorEventArgumentCaptorAllValues.get(1);
        assertThat(payloadMissingError.getName(), is(ErrorEvent.NAME));
        assertThat(payloadMissingError.errorType(), is(ClientError.BAD_REQUEST.getType()));
        assertThat(payloadMissingError.code(), is(ClientError.BAD_REQUEST.getClientCode()));
        assertThat(payloadMissingError.message(), is("'payload' missing"));

        verify(messageHandler).handle(eq(userSession), any(RequestWrapper.class));
    }

    @Test
    void onMessageException() {
        final var sessionId = UUID.randomUUID().toString();
        final var session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);
        final var async = mock(RemoteEndpoint.Async.Async.class);
        when(session.getAsyncRemote()).thenReturn(async);
        final var userSession = mock(UserSession.class);
        when(websocketRegistry.getSession(sessionId)).thenReturn(userSession);

        final Instance<MessageHandler> messageHandlerInstance = mock(Instance.class);
        final var messageHandler = mock(MessageHandler.class);
        when(messageHandlerInstance.get()).thenReturn(messageHandler);
        when(messageHandlers.select(any(Annotation.class))).thenReturn(messageHandlerInstance);
        final var message = "test message";
        doThrow(new RuntimeException(message)).when(messageHandler).handle(any(), any());

        socketV1.onMessage(session, "{}");

        verify(async).sendText("Could not read message " + message);
    }

    @Nested
    class OnMessageNested {

        public static final String MESSAGE_PLACEHOLDER = """
                {"event": "%s"}
                """;

        private Session session;
        private UserSession userSession;
        private String event = "test";

        @BeforeEach
        void beforeEach() {
            final var sessionId = UUID.randomUUID().toString();
            session = mock(Session.class);
            when(session.getId()).thenReturn(sessionId);

            userSession = mock(UserSession.class);
            when(websocketRegistry.getSession(sessionId)).thenReturn(userSession);
        }

        @Test
        void typedHandler() {
            final Instance<MessageHandler> messageHandlerInstance = mock(Instance.class);
            final var messageHandler = mock(MessageHandler.class);
            when(messageHandlerInstance.get()).thenReturn(messageHandler);
            when(messageHandlerInstance.isResolvable()).thenReturn(true);
            when(messageHandlers.select(NamedLiteral.of(event))).thenReturn(messageHandlerInstance);

            final var message = MESSAGE_PLACEHOLDER.formatted(event);

            socketV1.onMessage(session, message);

            verify(messageHandler).handle(eq(userSession), any(RequestWrapper.class));
        }

        @Test
        void defaultHandler() {
            final Instance<MessageHandler> typedMessageHandlerInstance = mock(Instance.class);
            when(typedMessageHandlerInstance.isResolvable()).thenReturn(false);
            when(messageHandlers.select(NamedLiteral.of(event))).thenReturn(typedMessageHandlerInstance);

            final Instance<MessageHandler> defaultMessageHandlerInstance = mock(Instance.class);
            final var messageHandler = mock(MessageHandler.class);
            when(defaultMessageHandlerInstance.get()).thenReturn(messageHandler);
            when(messageHandlers.select(new DefaultHandler.Literal())).thenReturn(defaultMessageHandlerInstance);

            final var message = MESSAGE_PLACEHOLDER.formatted(event);

            socketV1.onMessage(session, message);

            verify(messageHandler).handle(eq(userSession), any(RequestWrapper.class));
        }
    }

}
