package id.global.core.router.ws;

import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
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

import id.global.common.annotations.amqp.Message;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;
import id.global.iris.amqp.parsers.ExchangeParser;
import id.global.iris.models.irissubscription.ClientSessionClosed;

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
            final var clientSessionClosed = new ClientSessionClosed(sessionId, userId);
            final var messageAnnotation = clientSessionClosed.getClass().getAnnotation(Message.class);
            final var name = ExchangeParser.getFromAnnotationClass(messageAnnotation);
            final var msg = new RequestWrapper(name, null, objectMapper.valueToTree(clientSessionClosed));
            sendToSubscription(userSession, msg);
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
                log.info("noting to do");
                return;
            }
            RequestWrapper msg = objectMapper.readerFor(RequestWrapper.class).readValue(message);
            log.info("message: {}", msg);
            if (msg.event() == null) {
                session.getAsyncRemote().sendText("{\"event\": \"error\", \"payload\":\"'event' missing\" }");
            }
            if (msg.payload() == null) {
                session.getAsyncRemote().sendText("{\"event\": \"error\", \"payload\":\"'payload' missing\" }");
            }
            var userSession = websocketRegistry.getSession(session.getId());
            if ("subscribe".equals(msg.event())) {
                log.info("we need to handle logging in");
                if (websocketRegistry.subscribe(userSession, msg)) {
                    userSession.sendMessageRaw("subscribed");
                    //session.sendMessage(requestMessage.getClientTraceId(), "subscribed", null, "{\"success\": true}"
                } else {
                    userSession.sendMessageRaw("subscribe failed");
                }
                return;
            }

            sendToBackend(userSession, msg);
        } catch (Exception e) {
            log.error("Could not handle message", e);
            session.getAsyncRemote().sendText("Could not read message" + e.getMessage());
        }

    }

    private void sendToBackend(UserSession session, RequestWrapper requestWrapper) {
        var message = session.createBackendRequest(requestWrapper);

        backendService.sendToBackend(requestWrapper.event(), message);
    }

    private void sendToSubscription(UserSession session, RequestWrapper requestWrapper) {
        var message = session.createBackendRequest(requestWrapper);

        backendService.sendToSubscription(requestWrapper.event(), message);
    }
}
