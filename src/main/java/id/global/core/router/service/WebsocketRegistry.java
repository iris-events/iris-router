package id.global.core.router.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.client.AuthClient;
import id.global.core.router.events.HeartBeatEvent;
import id.global.core.router.model.AmqpMessage;
import id.global.core.router.model.ResponseHandler;
import id.global.core.router.model.UserSession;
import id.global.core.router.model.WSResponseHandler;
import io.quarkus.scheduler.Scheduled;

/**
 * @author Tomaž Cerar
 */
@ApplicationScoped
public class WebsocketRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketRegistry.class);

    protected static final Set<String> NON_RPC_DATATYPES = Set.of("subscribe-message",
            "unsubscribe-message",
            "session-closed",
            "log-users-activity");
    private final WSResponseHandler responseHandler;
    private final ObjectMapper objectMapper;

    protected final ConcurrentHashMap<String, UserSession> sockets = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Set<UserSession>> users = new ConcurrentHashMap<>();
    protected final RequestRegistry requestRegistry;

    public WebsocketRegistry(RequestRegistry requestRegistry, ObjectMapper objectMapper) {
        this.requestRegistry = requestRegistry;
        this.responseHandler = new WSResponseHandler(this, objectMapper);
        this.objectMapper = objectMapper;
    }

    public UserSession startSession(Session session, Map<String, List<String>> headers) {
        UserSession userSession = createUserSession(session, headers);
        String userId = userSession.getUserId();
        sockets.put(session.getId(), userSession);
        users.computeIfAbsent(userId, s -> new CopyOnWriteArraySet<>()).add(userSession);
        return userSession;
    }

    public UserSession getSession(String sessionId) {
        final var userSession = sockets.get(sessionId);
        if (userSession == null) {
            LOGGER.warn("User session not found.");
        }
        return userSession;
    }

    public boolean hasUserSession(String userId) {
        return users.containsKey(userId);
    }

    public Collection<UserSession> getAllSessions() {
        return sockets.values();
    }

    public Set<UserSession> getAllUserSessions(String userId) {
        return users.get(userId);
    }

    public UserSession removeSocket(String socketId) {
        LOGGER.trace("removing socket: {}", socketId);
        UserSession socket = sockets.remove(socketId);
        if (socket != null) {
            var userSockets = users.get(socket.getUserId());
            if (userSockets != null) {
                int count = userSockets.size();
                if (userSockets.remove(socket)) {
                    count--;
                }
                if (count <= 0) {
                    //LOGGER.info("[{}]-'{}' After socket removal there are no more sockets for user", socketId, socket.getUserId());
                    users.remove(socket.getUserId());
                } else {
                    //LOGGER.info("[{}]-'{}' After socket removal there are still '{}' sockets for user", socketId, socket.getUserId(), userSockets.size());

                }
            }

        }
        return socket;
    }

    public ResponseHandler getResponseHandler() {
        return responseHandler;
    }

    public UserSession createUserSession(Session session, Map<String, List<String>> headers) {
        return new UserSession(objectMapper, session, headers);
    }

    public void registerRequest(AmqpMessage message) {

        String dataType = message.eventType();
        if (NON_RPC_DATATYPES.contains(dataType)) {
            return;
        }

        requestRegistry.registerNewRequest(message, responseHandler);
    }

    public boolean logout(String sessionId) {
        UserSession session = sockets.get(sessionId);
        if (session == null) {
            LOGGER.warn("Could not find session: {}, cannot perform logout!", sessionId);
            return false;
        }
        String currentUserId = session.getUserId();
        String newUserId = session.logOut();
        updateUserId(currentUserId, newUserId);
        return true;
    }

    @Inject
    AuthClient authClient;

    public void updateUserId(String oldUserId, String newUserId) {
        LOGGER.info("updating identity for user: {}, --> {}", oldUserId, newUserId);
        Set<UserSession> sessions = users.computeIfAbsent(newUserId, s -> new CopyOnWriteArraySet<>());
        Set<UserSession> oldSessions = users.remove(oldUserId);
        if (oldSessions != null) {
            sessions.addAll(oldSessions);
        }
    }

    public boolean login(UserSession userSession, String authToken) {
        LOGGER.info("checking token: {}", authToken);
        var jwtToken = authClient.checkToken(authToken);
        if (jwtToken != null) {
            var oldId = userSession.getUserId();
            userSession.login(jwtToken);
            LOGGER.info("user logged in: {}, roles: {}", jwtToken.getSubject(), jwtToken.getGroups());
            updateUserId(oldId, userSession.getUserId());
            return true;
        } else {
            return false;
        }
    }

    /*
     * @Override
     * public boolean hasUserSession(String userId) {
     * var ret = super.hasUserSession(userId);
     * if (!ret) {
     * LOGGER.info("no session found for user: {}, userSessions: {}", userId, users);
     * }
     * return ret;
     * }
     */

    @Scheduled(delay = 10, delayUnit = TimeUnit.SECONDS, every = "30s")
    public void sendHeartBeat() {
        for (UserSession session : getAllSessions()) {
            session.ping();
            if (session.isSendHeartbeat()) {
                session.sendEvent(new HeartBeatEvent(), null);
            }
        }
    }

}
