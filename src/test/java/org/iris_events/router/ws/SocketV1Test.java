package org.iris_events.router.ws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.iris_events.router.ws.SocketV1.IRIS_SESSION_ID_HEADER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.iris_events.common.ErrorType;
import org.iris_events.common.message.ErrorMessage;
import org.iris_events.router.events.ErrorEvent;
import org.iris_events.router.service.BackendService;
import org.iris_events.router.service.WebsocketRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.iris_events.router.model.RequestWrapper;
import org.iris_events.router.model.UserSession;
import org.iris_events.router.ws.message.handler.DefaultHandler;
import org.iris_events.router.ws.message.handler.MessageHandler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

@QuarkusTest
class SocketV1Test {

    public static final String MESSAGE_PLACEHOLDER = """
            {"event": "%s", "payload": %s, "clientTraceId": "1234"}
            """;

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
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.getUserProperties()).thenReturn(Map.of(IRIS_SESSION_ID_HEADER, UUID.randomUUID().toString()));
        socketV1.onMessage(session, "");

        verifyNoInteractions(websocketRegistry);
        verifyNoInteractions(backendService);
    }

    @Test
    void onMessage() {
        final var sessionId = UUID.randomUUID().toString();
        final var irisSessionId = UUID.randomUUID().toString();
        final var session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUserProperties()).thenReturn(Map.of(IRIS_SESSION_ID_HEADER, irisSessionId));

        final var userSession = mock(UserSession.class);
        when(userSession.isValid()).thenReturn(true);
        when(websocketRegistry.getSession(sessionId)).thenReturn(userSession);

        final Instance<MessageHandler> messageHandlerInstance = mock(Instance.class);
        final var messageHandler = mock(MessageHandler.class);
        when(messageHandlerInstance.get()).thenReturn(messageHandler);
        when(messageHandlers.select(any(Annotation.class))).thenReturn(messageHandlerInstance);

        socketV1.onMessage(session, "{}");

        final var errorEventArgumentCaptor = ArgumentCaptor.forClass(ErrorMessage.class);
        verify(userSession, times(1)).sendErrorMessage(errorEventArgumentCaptor.capture(), eq(null));

        final var errorEventArgumentCaptorAllValues = errorEventArgumentCaptor.getAllValues();
        final var eventMissingError = errorEventArgumentCaptorAllValues.get(0);
        assertThat(eventMissingError.errorType(), is(ErrorType.BAD_PAYLOAD));
        assertThat(eventMissingError.code(), Matchers.is(ErrorEvent.EVENT_MISSING_CLIENT_CODE));
        assertThat(eventMissingError.message(), is("'event' missing"));

        verifyNoInteractions(messageHandler);
    }

    @Test
    void onMessageException() {
        final var sessionId = UUID.randomUUID().toString();
        final var irisSessionId = UUID.randomUUID().toString();
        final var session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUserProperties()).thenReturn(Map.of(IRIS_SESSION_ID_HEADER, irisSessionId));
        final var async = mock(RemoteEndpoint.Async.Async.class);
        when(session.getAsyncRemote()).thenReturn(async);
        final var userSession = mock(UserSession.class);
        when(websocketRegistry.getSession(sessionId)).thenReturn(userSession);

        final Instance<MessageHandler> messageHandlerInstance = mock(Instance.class);
        final var messageHandler = mock(MessageHandler.class);
        when(messageHandlerInstance.get()).thenReturn(messageHandler);
        when(messageHandlers.select(any(Annotation.class))).thenReturn(messageHandlerInstance);
        final var errorMessage = "test message";
        doThrow(new RuntimeException(errorMessage)).when(messageHandler).handle(any(), any());

        final var message = MESSAGE_PLACEHOLDER.formatted("test", "{  \"foo\": \"bar\" }");
        socketV1.onMessage(session, message);

        verify(async).sendText("Could not read message " + errorMessage);
    }

    @Nested
    class OnMessageNested {

        private final String event = "test";
        private Session session;
        private UserSession userSession;

        @BeforeEach
        void beforeEach() {
            final var sessionId = UUID.randomUUID().toString();
            final var irisSessionId = UUID.randomUUID().toString();
            session = mock(Session.class);
            when(session.getId()).thenReturn(sessionId);
            when(session.getUserProperties()).thenReturn(Map.of(IRIS_SESSION_ID_HEADER, irisSessionId));

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

            final var message = MESSAGE_PLACEHOLDER.formatted(event, "{}");

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

            final var message = MESSAGE_PLACEHOLDER.formatted(event, "{}");

            socketV1.onMessage(session, message);

            verify(messageHandler).handle(eq(userSession), any(RequestWrapper.class));
        }
    }

}
