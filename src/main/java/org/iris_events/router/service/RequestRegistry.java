package org.iris_events.router.service;

import static org.iris_events.common.MessagingHeaders.Message.DEVICE;
import static org.iris_events.common.MessagingHeaders.Message.IP_ADDRESS;
import static org.iris_events.common.MessagingHeaders.Message.REQUEST_REFERER;
import static org.iris_events.common.MessagingHeaders.Message.REQUEST_VIA;
import static org.iris_events.common.MessagingHeaders.Message.USER_AGENT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.iris_events.common.MDCProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.iris_events.router.model.AmqpMessage;
import org.iris_events.router.model.BackendRequest;
import org.iris_events.router.model.ResponseHandler;
import org.iris_events.router.model.ResponseMessageType;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.MDC;

/**
 * @author Tomaz Cerar
 */
@ApplicationScoped
public class RequestRegistry {
    private static final Logger requestLogger = LoggerFactory.getLogger("api");
    private static final Logger requestErrorLogger = LoggerFactory.getLogger("api.error");
    private static final Duration requestExpirationTime = Duration.ofSeconds(30);

    private static final Set<String> NON_LOGGABLE_REQUESTS = Set.of("subscribe-internal", "heartbeat", "session");
    private final ConcurrentHashMap<String, BackendRequest> requests = new ConcurrentHashMap<>();
    private final int requestTimeLogThreshold = 10; // 5ms

    public RequestRegistry() {

    }

    private void registerNewRequest(BackendRequest request) {
        requestLogger.trace("registering request {}", request);
        requests.put(request.requestId(), request);
    }

    public BackendRequest getActiveRequest(String requestId) {
        return requests.get(requestId);
    }

    public boolean isRequestValid(String requestId) {
        return requestId != null && requests.containsKey(requestId);
    }

    public void publishResponse(ResponseMessageType messageType, AmqpMessage message) {
        String correlationId = message.correlationId();
        BackendRequest request = requests.get(correlationId);
        if (request != null) {
            ResponseHandler handler = request.responseHandler();

            Duration duration = Duration.between(request.created(), Instant.now());
            if (duration.toMillis() >= requestTimeLogThreshold && !NON_LOGGABLE_REQUESTS.contains(request.dataType()) ) {
                requestLogger.info("Request for '{}', params: {} took {} ms", request.dataType(),
                        request.requestBody(), duration.toMillis());
            }
            handler.handle(messageType, message);
            if (requestLogger.isTraceEnabled()) {
                requestLogger.info("Request handled successfully - removing request.");
            }
            requests.remove(correlationId);
        } else {
            requestErrorLogger.warn("Could not properly handle message, request/correlation id no longer active.");
        }

    }

    @Scheduled(every = "10s", delay = 1)
    public void checkForExpiredRequests() {
        final Instant expireTime = Instant.now().minus(requestExpirationTime);
        requests.values().forEach(n -> {
            if (n.created().isBefore(expireTime)) {
                setDiagnosticData(n);
                String msg = String.format(
                            "Request '%s' timeout, data type: '%s' device: '%s', user agent: '%s', userId: '%s'",
                            n.requestId(), n.dataType(), n.device(), n.userAgent(), n.userId());

                requestErrorLogger.warn(msg);
                requests.remove(n.requestId());
                clearMCD();
            }
        });
    }

    private void setDiagnosticData(BackendRequest request) {
        MDC.put(MDCProperties.EVENT_TYPE, request.dataType());
        MDC.put("sentAt", request.created().toString());
        if (request.sessionId() != null) {
            MDC.put(MDCProperties.SESSION_ID, request.sessionId());
        }
        if (request.requestId() != null) {
            MDC.put(MDCProperties.CORRELATION_ID, request.requestId());
        }
        if (request.userId() != null) {
            MDC.put(MDCProperties.USER_ID, request.userId());
        }
        if (request.userAgent() != null) {
            MDC.put("userAgent", request.userAgent());
        }
        if (request.device() != null) {
            MDC.put("deviceId", request.device());
        }
        if (request.requestBody() != null) {
            MDC.put("payload", request.requestBody());
        }
    }

    private void clearMCD() {
        MDC.remove(MDCProperties.SESSION_ID);
        MDC.remove(MDCProperties.CORRELATION_ID);
        MDC.remove(MDCProperties.USER_ID);
        MDC.remove(MDCProperties.EVENT_TYPE);
        MDC.remove("deviceId");
    }

    public void registerNewRequest(AmqpMessage message, ResponseHandler responseHandler) {
        Map<String, Object> properties = message.properties().getHeaders();
        String eventType = message.eventType();
        String ipAddress = (String) properties.get(IP_ADDRESS);
        String userAgent = (String) properties.get(USER_AGENT);
        String referer = (String) properties.get(REQUEST_REFERER);
        String requestVia = (String) properties.get(REQUEST_VIA);
        String device = (String) properties.get(DEVICE);
        String userId = message.userId();
        String sessionId = message.sessionId();
        String request = message.body().copy().toString(StandardCharsets.UTF_8);
        registerNewRequest(
                new BackendRequest(message.correlationId(), eventType, Instant.now(), request, ipAddress, userAgent,
                        referer, requestVia, device, userId, sessionId, responseHandler));

    }

}
