package id.global.core.router.model;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
    protected void onSuccess(ResponseMessageType responseMessageType, AmqpMessage message) {
        MDC.put("sessionId", message.sessionId());
        if (message.clientTraceId() != null) {
            MDC.put("clientTraceId", message.clientTraceId());
        }
        MDC.put("correlationId", message.correlationId());
        MDC.put("eventType", message.eventType());
        MDC.put("userId", message.userId());
        if (responseMessageType == ResponseMessageType.RPC) {
            sendRPCMessage(message);
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
        //assert session != null;
        if (session == null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.warn("Could not find session on this router message: {}", BackendRequest.sanitizeBody(message.body()));
            }
            return;
        }
        session.sendMessage(message);
    }

    private void sendRPCMessage(AmqpMessage message) {
        String userId = message.userId();
        Set<? extends UserSession> allSocketOfTheUser = websocketRegistry.getAllUserSessions(userId);
        if (allSocketOfTheUser == null || allSocketOfTheUser.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("No sockets found to send RPC message. message: {}", BackendRequest.sanitizeBody(message.body()));
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
