package org.iris_events.router.ws;

import io.undertow.websockets.DefaultContainerConfigurator;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.List;
import java.util.UUID;

import static org.iris_events.router.ws.SocketV1.IRIS_SESSION_ID_HEADER;

public class WsContainerConfigurator extends DefaultContainerConfigurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        super.modifyHandshake(sec, request, response);
        String irisSessionId = UUID.randomUUID().toString();
        sec.getUserProperties().put(IRIS_SESSION_ID_HEADER, irisSessionId);
        response.getHeaders().put("Iris-Session-Id", List.of(irisSessionId));
        sec.getUserProperties().put("headers", request.getHeaders());
    }
}
