package id.global.core.router.model;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;

import id.global.core.router.consumer.AbstractWebSocketConsumer;

/**
 * @author Tomaz Cerar
 */
public class UserSession {
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    static String trimMessage(String message) {
        if (message == null) {
            return "empty";
        }
        if (message.length() > 300) {
            return message.substring(0, 300) + " ...";
        }
        return message;
    }

    private final ObjectMapper objectMapper;
    private final String socketId;
    private final Instant connectedAt;
    private String userId;
    private String trackingSessionId;
    private Instant blockedAt = Instant.EPOCH;
    private boolean anonymous;
    private Set<String> roles = Set.of();
    private Session session;
    private String token;
    private Instant tokenExpiry;
    private Map<String, Object> defaultMessageHeaders;
    private String clientIp;
    private String clientDeviceId = null;
    private boolean sendHeartbeat;
    private String userAgent;

    public UserSession(ObjectMapper objectMapper, Session session, Map<String, List<String>> headers) {
        this.objectMapper = objectMapper;
        this.session = Objects.requireNonNull(session);
        this.socketId = session.getId();
        this.userId = socketId; //for now as we don't have user_id
        this.anonymous = true;
        this.connectedAt = Instant.now();
        log.info("created new user session for userId: {}, socket id: {}", userId, socketId);
        setupDefaultHeaders(headers);
    }

    public String getId() {
        return socketId;
    }

    /*
     * public String getClientIp() {
     * return clientIp;
     * }
     */

    public Instant getConnectedAt() {
        return connectedAt;
    }

    //actions

    public void sendMessage(String correlationId, String eventType, String messageBody) {
        var message = convertResponse(messageBody, eventType, correlationId);
        sendMessageRaw(message);
    }

    public void sendMessageRaw(String stringMessage) {
        if (log.isTraceEnabled()) {
            log.trace("session: {}, message: {}", socketId, stringMessage);
        }

        if (!isValid()) {
            log.warn("[{}] We are trying to write '{}' to socket that is not valid, userId: {}", socketId,
                    trimMessage(stringMessage), userId);
            //sendEvent(new JWTExceptionEvent(jwtHash), null);
        } else {
            writeMessageDirect(stringMessage);
        }
    }

    private void writeMessageDirect(String msg) {
        session.getAsyncRemote().sendText(msg);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserSession that = (UserSession) o;
        return Objects.equals(socketId, that.socketId) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(socketId, userId);
    }

    public boolean isValid() {//todo implement more checks
        if (anonymous) {
            return true;
        }

        return tokenExpiry.isAfter(Instant.now());
    }

    public String logOut() {
        anonymous = true;
        token = null;
        return userId;
    }

    /**
     * @return true if socket is still valid after expire removal
     */
    public boolean removeExpired() {
        //todo
        return isValid();

    }

    public String getUserId() {
        return userId;
    }

    public void close() {
        log.trace("[{}] Closing websocket", socketId);
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "close"));
        } catch (IOException e) {
            log.warn("could not close socket", e);
        }

    }

    public void setUserHeaders(Map<String, Object> headers) {
        /*
         * if (anonymous) {
         * headers.put(SecurityTokenKind.ANONYMOUS.getValue(), anonymousUserId);
         * headers.put("userId", anonymousUserId);
         * } else {
         * headers.put(SecurityTokenKind.JWT.getValue(), jwtToken);
         * headers.put("userId", userId);
         * }
         * headers.putAll(defaultMessageHeaders);
         */
        //headers.put(SecurityTokenKind.ANONYMOUS.getValue(), anonymousUserId);
        headers.put("userId", userId);
        headers.putAll(defaultMessageHeaders);

    }

    private void setupDefaultHeaders(Map<String, List<String>> headers) {
        this.defaultMessageHeaders = new HashMap<>();

        this.clientIp = headers.getOrDefault("X-Envoy-External-Address", List.of())
                .stream()
                .findAny()
                .orElse(null);

        // getting proxy ip address from headers
        List<String> proxyIpList = headers.get("X-Forwarded-For");
        String proxyIp = null;
        if (proxyIpList != null) {
            List<String> proxyIpListFiltered = new ArrayList<>(); // proxyIpList is unmodifiable, create modifiable copy
            // if client ip exists in header remove it from proxy list, proxy ip should only contain real proxy ips
            if (clientIp != null) {
                for (String s : proxyIpList) {
                    if (!clientIp.equals(s)) {
                        proxyIpListFiltered.add(s);
                    }
                }
            }
            if (proxyIpListFiltered.size() > 0) {
                proxyIp = String.join(", ", proxyIpListFiltered);
            }
        }

        // getting user agent from headers
        String userAgent = null;
        List<String> userAgentList = headers.get("User-Agent");
        if (userAgentList != null) {
            userAgent = String.join(", ", userAgentList);
        }
        this.userAgent = userAgent;

        defaultMessageHeaders.put("proxyIpAddress", proxyIp);

        defaultMessageHeaders.put("userAgent", userAgent);
        defaultMessageHeaders.put("sessionId", session.getId());
        if (clientIp != null) {
            defaultMessageHeaders.put("ipAddress", clientIp);
        }
        if (clientDeviceId != null) {
            defaultMessageHeaders.put("device", clientDeviceId);
        }
    }

    public AmpqMessage createBackendRequest(RequestWrapper requestMessage, String traceId) {

        var dataType = requestMessage.event();
        var version = "1.0";

        var headers = new HashMap<String, Object>();
        setUserHeaders(headers);
        headers.put("event", dataType);
        headers.put("router", AbstractWebSocketConsumer.routerId);
        if (traceId != null) {
            headers.put("traceId", traceId);
        }
        headers.put("clientTraceId", requestMessage.clientTraceId());
        headers.put("X-Request-Via", "router/WebSocket");
        if (log.isTraceEnabled()) {
            var copy = new HashMap<>(headers);
            copy.put("jwt", ".....");
            log.trace("[{}] backend payload: {},  headers: {}", getUserId(), requestMessage.payload(), copy);
        }
        final AMQP.BasicProperties messageProperties = new AMQP.BasicProperties()
                .builder()
                .correlationId(traceId)
                .headers(headers)
                .build();
        return new AmpqMessage(writeValueAsBytes(requestMessage.payload()), messageProperties);

    }

    private byte[] writeValueAsBytes(Object value) throws RuntimeException {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not serialize to json", e);
        }
    }

    public void pong() {
        try {
            session.getAsyncRemote().sendPong(ByteBuffer.wrap("Hello".getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public void ping() {
        try {
            session.getAsyncRemote().sendPong(ByteBuffer.wrap("Hello".getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String convertResponse(String message, String eventType, String clientTraceId) {
        try {
            StringWriter writer = new StringWriter();
            JsonGenerator jGenerator = objectMapper.getFactory()
                    .createGenerator(writer);
            jGenerator.writeStartObject();
            jGenerator.writeStringField("event", eventType);
            if (clientTraceId != null) {
                jGenerator.writeStringField("clientTraceId", clientTraceId); //todo clientTraceId != correlationId
            }
            if (message != null && !message.isBlank()) {
                jGenerator.writeFieldName("payload");
                jGenerator.writeRawValue(message);
                jGenerator.writeEndObject();
            }

            jGenerator.close();
            return writer.toString();
        } catch (IOException e) {
            log.error("Could not convert json object", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "socketId='" + socketId + '\'' +
                ", userId='" + userId + '\'' +
                ", roles=" + roles +
                '}';
    }
}
