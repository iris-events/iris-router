package id.global.core.router.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.model.AmpqMessage;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.ResponseHandler;
import id.global.core.router.model.UserSession;
import id.global.core.router.model.WSResponseHandler;

/**
 * @author Tomaž Cerar
 */
@ApplicationScoped
public class WebsocketRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketRegistry.class);

    protected static final Set<String> NON_RPC_DATATYPES = Set.of("subscribe-message",
            "unsubscribe-message",
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
        //LOGGER.info("socket: {}", userSession);
        sockets.put(session.getId(), userSession);
        users.computeIfAbsent(userId, s -> new CopyOnWriteArraySet<>())
                .add(userSession);
        return userSession;
    }

    public UserSession getSession(String sessionId) {
        return sockets.get(sessionId);
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

    public void registerRequest(AmpqMessage message) {

        String dataType = message.eventType();
        if (NON_RPC_DATATYPES.contains(dataType)) {
            return;
        }

        requestRegistry.registerNewRequest(message, responseHandler);
    }

    public boolean login(String sessionId, String token, String clientTraceId) {
        UserSession sessionV2 = sockets.get(sessionId);
        if (sessionV2 == null) {
            LOGGER.warn("Could not find session: {}, cannot perform login!", sessionId);
            return false;
        }
        return login(sessionV2, token, clientTraceId);
    }

    public boolean logout(String sessionId) {
        UserSession session = sockets.get(sessionId);
        if (session == null) {
            LOGGER.warn("Could not find session: {}, cannot perform logout!", sessionId);
            return false;
        }
        String currentUserId = session.getUserId();
        String newUserId = session.logOut();
        //updateUserId(currentUserId, newUserId);
        return true;
    }

    private boolean login(UserSession userSession, String token, String clientTraceId) {

        return false;
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

    public boolean subscribe(UserSession userSession, RequestWrapper requestMessage) {
        /*
         * Subscribe subscribe = objectMapper.convertValue(requestMessage.getPayload(), Subscribe.class);
         * LOGGER.info("subscribe info: {}", subscribe);
         * userSession.init(subscribe.getDeviceId(), subscribe.getApplication(), subscribe.isHeartbeat());
         * if (subscribe.getToken() != null) {
         * return login(userSession, subscribe.getToken(), requestMessage.getClientTraceId());
         * }
         */
        return true;
    }

    /*
     * public void updateUserId(String oldUserId, String newUserId) {
     * LOGGER.info("updating identity for user: {}, --> {}", oldUserId, newUserId);
     * Set<UserSession> sessions = users.getOrDefault(newUserId, new CopyOnWriteArraySet<>());
     * Set<UserSession> oldSessions = users.remove(oldUserId);
     * if (oldSessions != null) {
     * sessions.addAll(oldSessions);
     * }
     * users.putIfAbsent(newUserId, sessions);
     * }
     */

}
