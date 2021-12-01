package id.global.core.router.model;

/**
 * @author Tomaz Cerar
 */
public interface ResponseHandler {
    void handle(ResponseMessageType responseMessageType, AmpqMessage message);
}
