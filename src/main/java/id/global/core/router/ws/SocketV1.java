package id.global.core.router.ws;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.events.ErrorCode;
import id.global.core.router.events.ErrorEvent;
import id.global.core.router.events.ErrorType;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;
import id.global.core.router.ws.message.handler.DefaultHandler;
import id.global.core.router.ws.message.handler.MessageHandler;
import id.global.iris.irissubscription.SessionClosed;

@ServerEndpoint(value = "/v0/websocket", configurator = WsContainerConfigurator.class)
@ApplicationScoped
public class SocketV1 {
    private static final Logger log = LoggerFactory.getLogger(SocketV1.class);

    @Inject
    protected ObjectMapper objectMapper;
    @Inject
    WebsocketRegistry websocketRegistry;
    @Inject
    BackendService backendService;
    @Inject
    @Any
    Instance<MessageHandler> messageHandlers;

    @OnOpen
    public void onOpen(Session session, EndpointConfig conf) {
        log.info("web socket {} opened, user props: {} ", session.getId(), conf.getUserProperties());
        Map<String, List<String>> headers = (Map<String, List<String>>) conf.getUserProperties().remove("headers");
        var userSession = websocketRegistry.startSession(session, headers);
        conf.getUserProperties().put("user-session", userSession);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        var userSession = websocketRegistry.removeSocket(session.getId());
        log.info("closing user session: {}, reason: {}", userSession, reason.getReasonPhrase());
        if (userSession != null) {
            userSession.close();
            final var userId = userSession.getUserId();
            final var sessionId = userSession.getId();
            final var sessionClosed = new SessionClosed(sessionId, userId);
            backendService.sendIrisEventToBackend(userSession, null, sessionClosed);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.warn("on error happened", throwable);
        onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "error " + throwable.getMessage()));
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        try {
            log.info("raw: {}", message);
            if (message.isEmpty()) {
                log.info("nothing to do");
                return;
            }
            final var msg = objectMapper.readValue(message, RequestWrapper.class);
            log.info("message: {}", msg);
            final var userSession = websocketRegistry.getSession(session.getId());
            if (msg.event() == null) {
                final var errorEvent = new ErrorEvent(ErrorType.BAD_REQUEST, ErrorCode.BAD_REQUEST, "'event' missing");
                userSession.sendEvent(errorEvent, msg.clientTraceId());
            }
            if (msg.payload() == null) {
                final var errorEvent = new ErrorEvent(ErrorType.BAD_REQUEST, ErrorCode.BAD_REQUEST, "'payload' missing");
                userSession.sendEvent(errorEvent, msg.clientTraceId());
            }

            final var messageHandler = getMessageHandler(msg.event());
            messageHandler.handle(userSession, msg);
        } catch (Exception e) {
            log.error("Could not handle message", e);
            session.getAsyncRemote().sendText("Could not read message " + e.getMessage());
        }
    }

    private MessageHandler getMessageHandler(final String eventName) {
        if (Objects.isNull(eventName) || eventName.isBlank()) {
            return getDefaultMessageHandler();
        }
        final var messageHandlerInstance = messageHandlers.select(NamedLiteral.of(eventName));

        if (messageHandlerInstance.isResolvable()) {
            return messageHandlerInstance.get();
        }

        return getDefaultMessageHandler();
    }

    private MessageHandler getDefaultMessageHandler() {
        return messageHandlers.select(DefaultHandler.Literal.INSTANCE).get();
    }

}
