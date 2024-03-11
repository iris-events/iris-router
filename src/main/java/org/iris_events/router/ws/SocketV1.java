package org.iris_events.router.ws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.spi.CDI;
import org.iris_events.router.config.RouterConfig;
import org.iris_events.router.events.ErrorEvent;
import org.iris_events.router.model.RequestWrapper;
import org.iris_events.router.model.UserSession;
import org.iris_events.router.model.sub.SessionClosed;
import org.iris_events.router.service.BackendService;
import org.iris_events.router.service.WebsocketRegistry;
import org.iris_events.router.ws.message.handler.DefaultHandler;
import org.iris_events.router.ws.message.handler.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.iris_events.common.MDCProperties;
import org.iris_events.common.ErrorType;
import org.iris_events.common.message.ErrorMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/v0/websocket", configurator = WsContainerConfigurator.class)
@ApplicationScoped
public class SocketV1 {
    private static final Logger log = LoggerFactory.getLogger(SocketV1.class);

    public static final String IRIS_SESSION_ID_HEADER = "x-iris-session-id";
    @Inject
    RouterConfig config;

    @Inject
    ObjectMapper objectMapper;
    @Inject
    WebsocketRegistry websocketRegistry;
    @Inject
    BackendService backendService;
    @Inject
    @Any
    Instance<MessageHandler> messageHandlers;

    private List<String> bannedUserAgents;
    private List<String> bannedClients;

    @PostConstruct
    void init(){
        log.info("SocketV1 initialized.\nBanned user agents: {}\nBanned client versions: {} ", config.bannedUserAgents(), config.bannedClientVersions());
        bannedUserAgents = config.bannedUserAgents();
        bannedClients = config.bannedClientVersions();
    }

    private boolean checkForBannedClient(Map<String, List<String>> headers) {
        var userAgent = headers.getOrDefault("User-Agent", null);
        var clientVersion = headers.getOrDefault("x-client-version", null);
        if (userAgent != null && !bannedUserAgents.isEmpty()) {
            if (bannedUserAgents.contains(userAgent.getFirst())) {
                log.info("User agent {} is banned", userAgent);
                return true;
            }
        }
        if (clientVersion != null && !bannedClients.isEmpty()) {
            if (bannedClients.contains(clientVersion.getFirst())) {
                log.info("Client version {} is banned", clientVersion);
                return true;
            }
        }
        return false;
    }


    @OnOpen
    public void onOpen(Session session, EndpointConfig conf) {
        try {
            final var irisSessionId = (String) conf.getUserProperties().get(IRIS_SESSION_ID_HEADER);
            MDC.put(MDCProperties.SESSION_ID, irisSessionId);



            Map<String, List<String>> headers = Optional
                    .ofNullable((Map<String, List<String>>) conf.getUserProperties().remove("headers"))
                    .orElse(Collections.emptyMap());

            headers.forEach((k, v) -> MDC.put("header."+k, v.toString()));

            if (checkForBannedClient(headers)) {
                log.warn("Bad client, closing websocket.");
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Bad client"));
                } catch (IOException e) {
                    //we don't care
                }
                return;
            }
            Log.infof("Web socket opened.");


            var userSession = websocketRegistry.startSession(session, headers);
            conf.getUserProperties().put("user-session", userSession);

        } finally {
            MDC.clear();
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        final var sessionId = (String) session.getUserProperties().get(IRIS_SESSION_ID_HEADER);
        MDC.put(MDCProperties.SESSION_ID, sessionId);
        var userSession = websocketRegistry.removeSocket(sessionId);
        Optional.ofNullable(userSession).ifPresent(us -> MDC.put(MDCProperties.USER_ID, us.getUserId()));
        log.info("Closing websocket user session. reason: {}, closeCode: {}", reason.getReasonPhrase(),
                reason.getCloseCode());
        if (userSession != null) {
            userSession.close(reason);
            final var userId = userSession.getUserId();
            final var sessionClosed = new SessionClosed(sessionId, userId);
            backendService.sendInternalEvent(userSession, null, sessionClosed);
        }
        MDC.remove(MDCProperties.SESSION_ID);
        MDC.remove(MDCProperties.USER_ID);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.warn("on error happened", throwable);
        onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "error " + throwable.getMessage()));
    }

    @OnMessage
    public void onMessage(Session session, String message) {

        final var sessionId = (String) session.getUserProperties().get(IRIS_SESSION_ID_HEADER);
        try {
            MDC.put(MDCProperties.SESSION_ID, sessionId);
            if (message.isEmpty()) {
                log.warn("Received empty message, discarding message.");
                return;
            }
            final var userSession = websocketRegistry.getSession(sessionId);
            if (userSession == null) {
                log.warn("No open user session found, discarding message.");
                return;
            }

            final var msgFromClient = objectMapper.readValue(message, RequestWrapper.class);
            final var correlationId = UUID.randomUUID().toString();
            MDC.put(MDCProperties.CORRELATION_ID, correlationId);
            userSession.setupMDC();
            final var msg = msgFromClient.withCorrelationId(correlationId);

            Optional.ofNullable(msg.clientTraceId())
                    .ifPresent(clientTraceId -> MDC.put(MDCProperties.CLIENT_TRACE_ID, msg.clientTraceId()));


            if (msg.event() == null) {
                log.warn("'event' information missing, discarding message");
                final var errorEvent = new ErrorMessage(ErrorType.BAD_PAYLOAD, ErrorEvent.EVENT_MISSING_CLIENT_CODE, "'event' missing");
                userSession.sendErrorMessage(errorEvent, msg.clientTraceId());
                return;
            }

            if (msg.payload() == null) {
                log.warn("'payload' missing, discarding message.");
                final var errorEvent = new ErrorMessage(ErrorType.BAD_PAYLOAD, ErrorEvent.PAYLOAD_MISSING_CLIENT_CODE,
                        "'payload' missing");
                userSession.sendErrorMessage(errorEvent, msg.clientTraceId());
                return;
            }

            MDC.put(MDCProperties.EVENT_TYPE, msg.event());
            //log.info("Handling websocket client message.");
            final var messageHandler = getMessageHandler(msg.event());
            messageHandler.handle(userSession, msg);
        } catch (Exception e) {
            log.error("Could not handle websocket client message", e);
            session.getAsyncRemote().sendText("Could not read message " + e.getMessage());
        }finally {
            MDC.remove(MDCProperties.SESSION_ID);
            MDC.remove(MDCProperties.EVENT_TYPE);
            MDC.remove(MDCProperties.CORRELATION_ID);
            UserSession.clearMDC();
            MDC.clear();
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
