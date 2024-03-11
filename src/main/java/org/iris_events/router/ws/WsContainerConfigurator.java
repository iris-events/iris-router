package org.iris_events.router.ws;

import io.undertow.websockets.DefaultContainerConfigurator;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.iris_events.router.ws.SocketV1.IRIS_SESSION_ID_HEADER;

public class WsContainerConfigurator extends DefaultContainerConfigurator {
    private static final Logger log = LoggerFactory.getLogger(WsContainerConfigurator.class);

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        String irisSessionId = UUID.randomUUID().toString();
        sec.getUserProperties().put(IRIS_SESSION_ID_HEADER, irisSessionId);
        response.getHeaders().put(IRIS_SESSION_ID_HEADER, List.of(irisSessionId));
        sec.getUserProperties().put("headers", request.getHeaders());
    }


}
