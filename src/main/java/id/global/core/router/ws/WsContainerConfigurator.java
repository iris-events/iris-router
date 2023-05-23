package id.global.core.router.ws;

import io.undertow.websockets.DefaultContainerConfigurator;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

public class WsContainerConfigurator extends DefaultContainerConfigurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        super.modifyHandshake(sec, request, response);
        sec.getUserProperties().put("headers", request.getHeaders());
    }
}
