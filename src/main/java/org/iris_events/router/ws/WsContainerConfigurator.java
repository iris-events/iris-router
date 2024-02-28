package org.iris_events.router.ws;

import io.undertow.websockets.DefaultContainerConfigurator;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.iris_events.router.config.RouterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.iris_events.router.ws.SocketV1.IRIS_SESSION_ID_HEADER;

public class WsContainerConfigurator extends DefaultContainerConfigurator {
    private static final Logger log = LoggerFactory.getLogger(WsContainerConfigurator.class);
    private List<String> bannedUserAgents;
    private List<String> bannedClients;

    private void initConfig() {
        if (bannedClients != null) {
            return;
        }
        RouterConfig config = CDI.current().select(RouterConfig.class).get();
        bannedUserAgents = config.bannedUserAgents();
        bannedClients = config.bannedClientVersions();
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        initConfig();
        if (checkForBannedClient(request.getHeaders())) {
            log.warn("Bad client, rejecting websocket upgrade handshake.");
            //this is ultra bad hack, but we need to close the connection, relies on internal implementation of undertow
            sneakyThrows(new NoSuchAlgorithmException("Bad client detected, closing connection."));
        }


        String irisSessionId = UUID.randomUUID().toString();
        sec.getUserProperties().put(IRIS_SESSION_ID_HEADER, irisSessionId);
        response.getHeaders().put(IRIS_SESSION_ID_HEADER, List.of(irisSessionId));
        sec.getUserProperties().put("headers", request.getHeaders());
    }

    private boolean checkForBannedClient(Map<String, List<String>> headers) {
        var userAgent = headers.getOrDefault("User-Agent", null);
        var clientVersion = headers.getOrDefault("x-client-version", null);
        if (userAgent != null && !bannedUserAgents.isEmpty()) {
            if (bannedUserAgents.contains(userAgent.getFirst())) {
                log.info("User agent {} is banned", userAgent);
                return true;
            }
        }
        if (clientVersion != null && !bannedClients.isEmpty()) {
            if (bannedClients.contains(clientVersion.getFirst())) {
                log.info("Client version {} is banned", clientVersion);
                return true;
            }
        }
        return false;
    }

    public static <E extends Throwable> void sneakyThrows(Throwable e) throws E {
        throw (E) e;
    }
}
