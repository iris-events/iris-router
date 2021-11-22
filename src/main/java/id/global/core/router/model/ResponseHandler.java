package id.global.core.router.model;

/**
 * @author Tomaz Cerar
 */
public interface ResponseHandler {
    void handle(ResponseMessageType responseMessageType, String correlationId, String userId, String sessionId,
            String eventType, String message);
}
