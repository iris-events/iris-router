package id.global.core.router.ws;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.websockets.DefaultContainerConfigurator;

public class WsContainerConfigurator extends DefaultContainerConfigurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        super.modifyHandshake(sec, request, response);
        sec.getUserProperties().put("headers", request.getHeaders());
    }
}
