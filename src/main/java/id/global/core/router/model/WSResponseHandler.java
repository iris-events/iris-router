package id.global.core.router.model;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.service.WebsocketRegistry;

/**
 * @author Tomaz Cerar
 */
public class WSResponseHandler extends DefaultResponseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WSResponseHandler.class);

    private final WebsocketRegistry websocketRegistry;
    protected final ObjectMapper objectMapper;

    public WSResponseHandler(WebsocketRegistry websocketRegistry, ObjectMapper objectMapper) {
        this.websocketRegistry = websocketRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    protected void onSuccess(ResponseMessageType responseMessageType, String correlationId, String userId, String sessionId,
            String eventType, String messageBody) {
        if (responseMessageType == ResponseMessageType.RPC) {
            sendRPCMessage(userId, correlationId, eventType, messageBody);
        } else if (responseMessageType == ResponseMessageType.SESSION) {
            sendToSession(correlationId, userId, sessionId, eventType, messageBody);
        } else if (responseMessageType == ResponseMessageType.BROADCAST) {
            sendBroadcastMessage(eventType, messageBody);
        } else {
            throw new RuntimeException("Don't know how to handle: " + responseMessageType);
        }
    }

    private void sendToSession(String correlationId, String userId, String sessionId, String eventType, String messageBody) {
        if (sessionId == null || sessionId.isBlank()) {
            LOGGER.warn("Could not send session message with sessionId: {}, requestId: {}, userId: {}, message: {}", sessionId,
                    correlationId, userId, BackendRequest.sanitizeBody(messageBody));
            return;
        }
        UserSession session = websocketRegistry.getSession(sessionId);
        //assert session != null;
        if (session == null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn("Could not find session with sessionId: {} on this router, requestId: {}, userId: {}, message: {}",
                        sessionId, correlationId, userId, BackendRequest.sanitizeBody(messageBody));
            }
            return;
        }
        session.sendMessage(correlationId, eventType, messageBody);

    }

    private void sendRPCMessage(String userId, String requestId, String dataType, String messageBody) {
        Set<? extends UserSession> allSocketOfTheUser = websocketRegistry.getAllUserSessions(userId);
        if (allSocketOfTheUser == null || allSocketOfTheUser.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("There are no sockets for user: {}, request: {}! dropping message, event: {},  body{}", userId,
                        requestId, messageBody, dataType);
            }
            return;
        }
        for (UserSession session : allSocketOfTheUser) {
            session.sendMessage(requestId, dataType, messageBody);
        }
    }

    private void sendBroadcastMessage(String dataType, String message) {
        for (UserSession userSession : websocketRegistry.getAllSessions()) {
            userSession.sendMessage(null, dataType, message);
        }
    }

}
