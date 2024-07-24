package org.iris_events.router.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import org.iris_events.router.config.RouterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.iris_events.router.client.AuthClient;
import org.iris_events.router.events.HeartBeatEvent;
import org.iris_events.router.model.AmqpMessage;
import org.iris_events.router.model.ResponseHandler;
import org.iris_events.router.model.UserSession;
import org.iris_events.router.model.WSResponseHandler;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

/**
 * @author Tomaž Cerar
 */
@ApplicationScoped
public class WebsocketRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketRegistry.class);

    private static final Set<String> NON_RPC_DATATYPES = Set.of("subscribe-message",
            "unsubscribe-message",
            "session-closed");
    private final WSResponseHandler responseHandler;
    private final ObjectMapper objectMapper;

    protected final ConcurrentHashMap<String, UserSession> sockets = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Set<UserSession>> users = new ConcurrentHashMap<>();
    protected final RequestRegistry requestRegistry;
    private final List<String> nonRpcEvents;

    public WebsocketRegistry(RequestRegistry requestRegistry, ObjectMapper objectMapper, RouterConfig config) {
        this.requestRegistry = requestRegistry;
        this.responseHandler = new WSResponseHandler(this, objectMapper);
        this.objectMapper = objectMapper;
        this.nonRpcEvents = new ArrayList<>(config.nonRpcEvents());
        LOGGER.info("non rpc requests: {}", nonRpcEvents);
        this.nonRpcEvents.addAll(NON_RPC_DATATYPES);
    }

    public UserSession startSession(Session session, Map<String, List<String>> headers) {
        UserSession userSession = createUserSession(session, headers);
        String userId = userSession.getUserId();
        sockets.put(userSession.getId(), userSession);
        users.computeIfAbsent(userId, s -> new CopyOnWriteArraySet<>()).add(userSession);
        return userSession;
    }

    public UserSession getSession(String sessionId) {
        final var userSession = sockets.get(sessionId);
        if (LOGGER.isTraceEnabled()) {
            if (userSession == null) {

                LOGGER.warn("User session not found.");
            }
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

        String eventType = message.eventType();
        if (nonRpcEvents.contains(eventType)) {
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
        if (oldUserId.equals(newUserId)) {
            return;
        }
        LOGGER.info("updating identity for user: {}, --> {}", oldUserId, newUserId);
        Set<UserSession> sessions = users.computeIfAbsent(newUserId, s -> new CopyOnWriteArraySet<>());
        Set<UserSession> oldSessions = users.remove(oldUserId);
        if (oldSessions != null) {
            sessions.addAll(oldSessions);
        }
    }

    public boolean login(UserSession userSession, String authToken) {
        var jwtToken = authClient.checkToken(authToken);
        if (jwtToken != null) {
            var oldId = userSession.getUserId();
            userSession.login(jwtToken);
            LOGGER.info("user logged in: {}, roles: {}, token expiry: {}", jwtToken.getSubject(), jwtToken.getGroups(), jwtToken.getExpirationTime());
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
