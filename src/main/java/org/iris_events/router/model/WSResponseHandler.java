package org.iris_events.router.model;

import java.util.List;
import java.util.Set;

import org.iris_events.router.service.WebsocketRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Tomaz Cerar
 */
public class WSResponseHandler extends DefaultResponseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WSResponseHandler.class);

    private final WebsocketRegistry websocketRegistry;
    protected final ObjectMapper objectMapper;
    private final List<String> nonRpcEvents;

    public WSResponseHandler(WebsocketRegistry websocketRegistry, ObjectMapper objectMapper, List<String> nonRpcEvents) {
        this.websocketRegistry = websocketRegistry;
        this.objectMapper = objectMapper;
        this.nonRpcEvents = nonRpcEvents;
    }

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    protected void onSuccess(ResponseMessageType responseMessageType, AmqpMessage message) {
        if (responseMessageType == ResponseMessageType.USER) {
            sendUserMessage(message);
        } else if (responseMessageType == ResponseMessageType.SESSION) {
            sendToSession(message);
        } else if (responseMessageType == ResponseMessageType.BROADCAST) {
            sendBroadcastMessage(message);
        } else {
            throw new RuntimeException("Don't know how to handle: " + responseMessageType);
        }
    }


    @Override
    protected void onFailure(final AmqpMessage message) {
        // always send error message only to current active session
        sendToSession(message);
    }

    private void sendToSession(AmqpMessage message) {
        String sessionId = message.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            LOGGER.warn("Could not send session message. message: {}", BackendRequest.sanitizeBody(message.body()));
            return;
        }
        UserSession session = websocketRegistry.getSession(sessionId);
        // assert session != null;
        if (session == null && !nonRpcEvents.contains(message.eventType())) {
            LOGGER.warn("Could not find session on this router message: {}", BackendRequest.sanitizeBody(message.body()));
            return;
        }
        session.sendMessage(message);
    }

    private void sendUserMessage(AmqpMessage message) {
        String userId = message.userId();
        log.trace("sending message to user: {}", userId);
        Set<? extends UserSession> allSocketOfTheUser = websocketRegistry.getAllUserSessions(userId);
        if (allSocketOfTheUser == null || allSocketOfTheUser.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn("No sockets found to send RPC message. message: {}", BackendRequest.sanitizeBody(message.body()));
            }
            return;
        }
        for (UserSession session : allSocketOfTheUser) {
            session.sendMessage(message);
        }
    }

    private void sendBroadcastMessage(AmqpMessage message) {
        for (UserSession userSession : websocketRegistry.getAllSessions()) {
            userSession.sendMessage(message);
        }
    }

}
