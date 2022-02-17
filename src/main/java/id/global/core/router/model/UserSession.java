package id.global.core.router.model;

import static id.global.common.headers.amqp.MessagingHeaders.Message.ANONYMOUS_ID;
import static id.global.common.headers.amqp.MessagingHeaders.Message.CLIENT_TRACE_ID;
import static id.global.common.headers.amqp.MessagingHeaders.Message.DEVICE;
import static id.global.common.headers.amqp.MessagingHeaders.Message.EVENT_TYPE;
import static id.global.common.headers.amqp.MessagingHeaders.Message.IP_ADDRESS;
import static id.global.common.headers.amqp.MessagingHeaders.Message.JWT;
import static id.global.common.headers.amqp.MessagingHeaders.Message.PROXY_IP_ADDRESS;
import static id.global.common.headers.amqp.MessagingHeaders.Message.REQUEST_VIA;
import static id.global.common.headers.amqp.MessagingHeaders.Message.ROUTER;
import static id.global.common.headers.amqp.MessagingHeaders.Message.SESSION_ID;
import static id.global.common.headers.amqp.MessagingHeaders.Message.USER_AGENT;
import static id.global.common.headers.amqp.MessagingHeaders.Message.USER_ID;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.websocket.CloseReason;
import javax.websocket.Session;

import org.eclipse.microprofile.jwt.JsonWebToken;
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
    private final String anonymousUserId;

    static String trimMessage(String message) {
        if (message == null) {
            return "empty";
        }
        if (message.length() > 300) {
            return message.substring(0, 300) + " ...";
        }
        return message;
    }

    private static String generateAnonymousUserId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return "a-" + sessionId;
        } else {
            return "a-" + UUID.randomUUID()
                    .toString()
                    .substring(2);
        }
    }

    private final ObjectMapper objectMapper;
    private final String socketId;
    private final Instant connectedAt;
    private String userId;
    private Instant blockedAt = Instant.EPOCH;
    private boolean anonymous;
    private Set<String> roles = Set.of();
    private final Session session;
    private JsonWebToken token;
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
        this.anonymousUserId = generateAnonymousUserId(session.getId());
        this.userId = anonymousUserId;
        this.anonymous = true;
        this.connectedAt = Instant.now();
        log.info("created new user session for userId: {}, socket id: {}", userId, socketId);
        setupDefaultHeaders(headers);
    }

    public String getId() {
        return socketId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    //actions

    public void sendMessage(AmpqMessage message) {
        var msg = convertResponse(message);
        sendMessageRaw(msg);
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

    public boolean isValid() {//todo implement more checks
        if (anonymous) {
            return true;
        }

        return tokenExpiry.isAfter(Instant.now());
    }

    public void login(JsonWebToken token) {
        if (token.getSubject().equals(this.userId)) {
            log.info("user is same, JWT has has updated, new JWT has roles: {}", token.getGroups());
        }

        this.userId = token.getSubject();
        update(token);
        anonymous = false;
    }

    public void update(JsonWebToken token) {
        if (!token.getSubject()
                .equals(this.userId)) {
            log.warn("Could not update token for different user, sent user: {}, our user: {}", token.getSubject(), this.userId);
            return;
        }
        this.token = token;
        this.roles = token.getGroups();
        this.tokenExpiry = Instant.ofEpochSecond(token.getExpirationTime());
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

    public void setBackendMessageHeaders(Map<String, Object> headers) {
        if (anonymous) {
            headers.put(ANONYMOUS_ID, anonymousUserId);
            headers.put(USER_ID, anonymousUserId);
        } else {
            headers.put(JWT, token.getRawToken());
            headers.put(USER_ID, userId);
        }
        headers.putAll(defaultMessageHeaders);
    }

    private void setupDefaultHeaders(Map<String, List<String>> headers) {
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
        this.defaultMessageHeaders = new HashMap<>();
        defaultMessageHeaders.put(PROXY_IP_ADDRESS, proxyIp);

        defaultMessageHeaders.put(USER_AGENT, userAgent);
        defaultMessageHeaders.put(SESSION_ID, session.getId());
        defaultMessageHeaders.put(USER_ID, userId);
        if (clientIp != null) {
            defaultMessageHeaders.put(IP_ADDRESS, clientIp);
        }
        if (clientDeviceId != null) {
            defaultMessageHeaders.put(DEVICE, clientDeviceId);
        }
        defaultMessageHeaders.put(ROUTER, AbstractWebSocketConsumer.routerId);
    }

    public AmpqMessage createBackendRequest(RequestWrapper requestMessage) {

        var eventType = requestMessage.event();

        var headers = new HashMap<String, Object>();
        setBackendMessageHeaders(headers);
        headers.put(EVENT_TYPE, eventType);
        headers.put(CLIENT_TRACE_ID, requestMessage.clientTraceId());
        headers.put(REQUEST_VIA, "router/WebSocket");
        if (log.isTraceEnabled()) {
            var copy = new HashMap<>(headers);
            log.trace("[{}] backend payload: {},  headers: {}", getUserId(), requestMessage.payload(), copy);
        }
        String correlationId = UUID.randomUUID().toString();
        final AMQP.BasicProperties messageProperties = new AMQP.BasicProperties()
                .builder()
                .correlationId(correlationId)
                .timestamp(new Date())
                .headers(headers)
                .build();
        return new AmpqMessage(writeValueAsBytes(requestMessage.payload()), messageProperties, eventType);

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

    private String convertResponse(AmpqMessage message) {
        try {
            StringWriter writer = new StringWriter();
            JsonGenerator jGenerator = objectMapper.getFactory()
                    .createGenerator(writer);
            jGenerator.writeStartObject();
            jGenerator.writeStringField("event", message.eventType());
            if (message.clientTraceId() != null) {
                jGenerator.writeStringField("clientTraceId", message.clientTraceId());
            }
            if (message.body() != null) {
                jGenerator.writeFieldName("payload");
                jGenerator.writeRawValue(new String(message.body(), StandardCharsets.UTF_8));
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
}
