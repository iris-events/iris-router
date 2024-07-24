package org.iris_events.router.model;

import static org.iris_events.common.MessagingHeaders.Message.ANONYMOUS_ID;
import static org.iris_events.common.MessagingHeaders.Message.CLIENT_TRACE_ID;
import static org.iris_events.common.MessagingHeaders.Message.CLIENT_VERSION;
import static org.iris_events.common.MessagingHeaders.Message.DEVICE;
import static org.iris_events.common.MessagingHeaders.Message.EVENT_TYPE;
import static org.iris_events.common.MessagingHeaders.Message.IP_ADDRESS;
import static org.iris_events.common.MessagingHeaders.Message.JWT;
import static org.iris_events.common.MessagingHeaders.Message.PROXY_IP_ADDRESS;
import static org.iris_events.common.MessagingHeaders.Message.REQUEST_VIA;
import static org.iris_events.common.MessagingHeaders.Message.ROUTER;
import static org.iris_events.common.MessagingHeaders.Message.SESSION_ID;
import static org.iris_events.common.MessagingHeaders.Message.USER_AGENT;
import static org.iris_events.common.MessagingHeaders.Message.USER_ID;
import static org.iris_events.router.ws.SocketV1.IRIS_SESSION_ID_HEADER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.iris_events.common.MDCProperties;
import org.iris_events.router.events.ErrorEvent;
import org.iris_events.router.events.RouterEvent;
import org.iris_events.router.service.RouterIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;

import org.iris_events.common.ErrorType;
import org.iris_events.common.message.ErrorMessage;
import io.vertx.core.buffer.Buffer;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.slf4j.MDC;

/**
 * @author Tomaz Cerar
 */
@RegisterForReflection
public class UserSession {
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final String anonymousUserId;
    private String clientVersion = null;

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
    private boolean anonymous;
    private Set<String> roles = Set.of();
    private final Session session;
    private JsonWebToken token;
    private Instant tokenExpiry;
    private Map<String, Object> defaultMessageHeaders;
    private String clientIp;
    private String clientDeviceId = null;
    private boolean sendHeartbeat = false;
    private String userAgent;

    public UserSession(ObjectMapper objectMapper, Session session, Map<String, List<String>> headers) {
        this.objectMapper = objectMapper;
        this.session = Objects.requireNonNull(session);
        this.socketId = (String) session.getUserProperties().get(IRIS_SESSION_ID_HEADER);
        this.anonymousUserId = generateAnonymousUserId(socketId);
        this.userId = anonymousUserId;
        this.anonymous = true;
        this.connectedAt = Instant.now();
        setupDefaultHeaders(socketId, headers);
        setupMDC();
        log.info("Created new user session. userId: {}", userId);
        clearMDC();
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

    // actions
    public void sendMessage(AmqpMessage message) {
        final var clientTraceId = message.clientTraceId();
        final var rawMessage = RawMessage.builder(objectMapper)
                .setEventName(message.eventType())
                .setClientTraceId(clientTraceId)
                .setSubscriptionId(message.subscriptionId())
                .setPayloadFromBuffer(message.body())
                .build();

        sendMessageRaw(rawMessage, clientTraceId);
    }

    public void sendErrorMessage(ErrorMessage errorMessage, String clientTraceId) {
        final var routerEvent = getErrorEvent(errorMessage);
        sendEvent(routerEvent, clientTraceId);
    }

    public void sendEvent(RouterEvent event, String clientTraceId) {
        final var rawMessage = getRawMessage(event, clientTraceId);
        sendMessageRaw(rawMessage, clientTraceId);
    }

    public void sendMessageRaw(final RawMessage rawMessage, final String clientTraceId) {
        final var stringMessage = rawMessage.getMessage();
        if (log.isTraceEnabled()) {
            log.info("message: {}", stringMessage);
        }

        if (!isValid()) {
            MDC.put("tokenExpiry", tokenExpiry.toString());
            log.warn("[{}] We are trying to write '{}' to socket that is not valid, userId: {}. Closing the socket.", socketId,
                    trimMessage(stringMessage), userId);
            MDC.remove("tokenExpiry");
            sendSessionInvalidError(clientTraceId);
            /*try {
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ErrorEvent.UNAUTHORIZED_CLIENT_CODE));
            } catch (IOException e) {
                log.warn("Could not close socket", e);
            }*/
            return;
        }
        writeMessageDirect(stringMessage);

    }

    private void writeMessageDirect(String msg) {
        session.getAsyncRemote().sendText(msg);

    }

    public boolean isValid() {
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
        updateToken(token);
        anonymous = false;
    }

    private void updateToken(JsonWebToken token) {
        if (this.token != null && this.token.getTokenID().equals(token.getTokenID())) {
            log.info("Token is same, not updating");
            return;
        }
        if (!token.getSubject()
                .equals(this.userId)) {
            log.warn("Could not update token for different user, sent user: {}, our user: {}", token.getSubject(), this.userId);
            return;
        }
        this.token = token;
        this.roles = token.getGroups();
        this.tokenExpiry = Instant.ofEpochSecond(token.getExpirationTime())
                .plus(30, ChronoUnit.SECONDS); //add 30 seconds grace period
        log.info("token expiry: {}",tokenExpiry);
        if (Instant.now().getEpochSecond() > token.getExpirationTime()){
            log.warn("Token is already expired, token expiry: {}, issued at: {}", token.getExpirationTime(), token.getIssuedAtTime());
        }
    }

    public void updateDeviceId(String clientDeviceId) {
        log.info("setting device id: {}",clientDeviceId);
        this.clientDeviceId = clientDeviceId;
    }

    public String logOut() {
        anonymous = true;
        token = null;
        return userId;
    }

    public String getUserId() {
        return userId;
    }

    public String getClientDeviceId() {
        return clientDeviceId;
    }

    public boolean isSendHeartbeat() {
        return sendHeartbeat;
    }

    public void setSendHeartbeat(boolean sendHeartbeat) {
        this.sendHeartbeat = sendHeartbeat;
    }

    public void close(final CloseReason reason) {
        log.trace("[{}] Closing websocket", socketId);
        try {
            session.close(reason);
        } catch (IOException e) {
            log.warn("could not close socket", e);
        }

    }

    public void setBackendMessageHeaders(Map<String, Object> headers) {
        headers.putAll(defaultMessageHeaders);
        if (anonymous) {
            headers.put(ANONYMOUS_ID, anonymousUserId);
            headers.put(USER_ID, anonymousUserId);
        } else {
            headers.put(JWT, token.getRawToken());
            headers.put(USER_ID, userId);
        }
    }

    public AmqpMessage createBackendRequest(RequestWrapper requestMessage) {
        var eventType = requestMessage.event();
        var headers = new LinkedHashMap<String, Object>();
        setBackendMessageHeaders(headers);
        headers.put(EVENT_TYPE, eventType);
        if (requestMessage.clientTraceId() != null) {
            headers.put(CLIENT_TRACE_ID, requestMessage.clientTraceId());
        }
        headers.put(REQUEST_VIA, "router/WebSocket");
        if (log.isTraceEnabled()) {
            var copy = new HashMap<>(headers);
            setupMDC();
            MDC.put(MDCProperties.USER_ID, userId);
            MDC.put("payload",  requestMessage.payload().toString());
            MDC.put("headers",  copy.toString());
            log.trace("[{}] sending to backend",eventType);
            clearMDC();
        }

        final AMQP.BasicProperties messageProperties = new AMQP.BasicProperties()
                .builder()
                .correlationId(requestMessage.correlationId())
                .timestamp(new Date())
                .headers(headers)
                .build();
        return new AmqpMessage(writeValueAsBytes(requestMessage.payload()), messageProperties, eventType);
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

    public void sendSessionInvalidError(final String clientTraceId) {
        log.info("sending session invalid error to client, token expired: {}", socketId);
        final var errorEvent = getErrorEvent(
                new ErrorMessage(ErrorType.UNAUTHORIZED, ErrorEvent.TOKEN_EXPIRED_CLIENT_CODE, "Token has expired."));
        final var rawErrorMessage = getRawMessage(errorEvent, clientTraceId);
        writeMessageDirect(rawErrorMessage.getMessage());
    }

    private Buffer writeValueAsBytes(Object value) throws RuntimeException {
        try {
            return Buffer.buffer(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not serialize to json", e);
        }
    }

    private void setupDefaultHeaders(String sessionId, Map<String, List<String>> headers) {
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
                if (!proxyIpListFiltered.isEmpty()) {
                    proxyIp = String.join(", ", proxyIpListFiltered);
                }
            }else{
                clientIp = proxyIpList.getFirst();
            }
        }
        if (clientIp == null) {
            log.warn("Could not find client ip from headers, tried 'X-Envoy-External-Address','X-Forwarded-For' sessionId: {}", sessionId);
        }

        // getting user agent from headers
        String userAgent = null;
        List<String> userAgentList = headers.get("User-Agent");
        if (userAgentList != null) {
            userAgent = String.join(", ", userAgentList);
        }
        this.userAgent = userAgent;
        var deviceIds = headers.get("device_id");
        if (deviceIds !=null && !deviceIds.isEmpty()){
            clientDeviceId = deviceIds.getFirst();
        }


        var clientVersionList = headers.get("x-client-version");
        if(clientVersionList != null && !clientVersionList.isEmpty()) {
            clientVersion = clientVersionList.getFirst();
        }

        this.defaultMessageHeaders = new HashMap<>();
        defaultMessageHeaders.put(PROXY_IP_ADDRESS, proxyIp);

        defaultMessageHeaders.put(USER_AGENT, userAgent);
        defaultMessageHeaders.put(SESSION_ID, sessionId);
        defaultMessageHeaders.put(USER_ID, userId);
        if (clientIp != null) {
            defaultMessageHeaders.put(IP_ADDRESS, clientIp);
        }
        if (clientDeviceId != null) {
            defaultMessageHeaders.put(DEVICE, clientDeviceId);
        }
        if (clientVersion != null) {
            defaultMessageHeaders.put(CLIENT_VERSION, clientVersion);
        }
        defaultMessageHeaders.put(ROUTER, RouterIdProvider.routerId());
    }
    public void setupMDC() {
        if (clientVersion != null) {
            MDC.put("clientVersion", clientVersion);
        }
        if (clientDeviceId != null) {
            MDC.put("deviceId", clientDeviceId);
        }
    }

    public static void clearMDC(){
        MDC.remove("clientVersion");
        MDC.remove(MDCProperties.USER_ID);
    }

    private RawMessage getRawMessage(final RouterEvent event, final String clientTraceId) {
        return RawMessage.builder(objectMapper)
                .setEventName(event.getName())
                .setClientTraceId(clientTraceId)
                .setPayloadFromEvent(event)
                .build();
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

    private static ErrorEvent getErrorEvent(final ErrorMessage errorMessage) {
        return new ErrorEvent(errorMessage.errorType(), errorMessage.code(), errorMessage.message());
    }
}
