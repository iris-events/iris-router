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

import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;

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
        //userSession.sendMessageRaw("hello from server");
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        var userSession = websocketRegistry.removeSocket(session.getId());
        log.info("closing user session: {}", userSession);
        if (userSession != null) {
            userSession.close();
        }

        //broadcast("User " + username + " left");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        //sessions.remove(username);
        //broadcast("User " + username + " left on error: " + throwable);
        log.info("on error happened", throwable);
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
            var userSession = websocketRegistry.getSession(session.getId());
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
}
