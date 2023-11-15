package org.iris_events.router.service;

import static org.iris_events.common.MessagingHeaders.Message.DEVICE;
import static org.iris_events.common.MessagingHeaders.Message.IP_ADDRESS;
import static org.iris_events.common.MessagingHeaders.Message.REQUEST_REFERER;
import static org.iris_events.common.MessagingHeaders.Message.REQUEST_URI;
import static org.iris_events.common.MessagingHeaders.Message.REQUEST_VIA;
import static org.iris_events.common.MessagingHeaders.Message.USER_AGENT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.iris_events.common.MessagingHeaders;
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
    //private static final Logger requestLogger = LoggerFactory.getLogger("api.ws");
    private static final Logger requestErrorLogger = LoggerFactory.getLogger("api.error");
    private static final Duration requestExpirationTime = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, BackendRequest> requests = new ConcurrentHashMap<>();
    private final int requestTimeLogThreshold = 5; // 5ms

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
            if (duration.toMillis() >= requestTimeLogThreshold) {
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
        final Instant expireTime = Instant.now().minus(Duration.ofSeconds(30));
        requests.values().forEach(n -> {
            if (n.created().isBefore(expireTime)) {
                setDiagnosticData(n);
                String msg;
                if (n.requestUri() == null) {
                    msg = String.format(
                            "Request '%s' timeout, data type: '%s' device: '%s', original request: '%s' user agent: '%s', userId: '%s'",
                            n.requestId(), n.dataType(), n.device(), n.requestBody(), n.userAgent(), n.userId());
                } else {
                    msg = String.format(
                            "Request '%s' timeout, requestUri: %s, referer: '%s', request via: '%s',  original request: '%s' user agent: '%s'",
                            n.requestId(), n.requestUri(), n.referer(), n.requestVia(), n.requestBody(), n.userAgent());
                }

                requestErrorLogger.warn(msg);
                requests.remove(n.requestId());

            }
        });
    }
    private void setDiagnosticData(BackendRequest request){
        if (request.sessionId() != null) {
            MDC.put("sessionId", request.sessionId());
        }
        if (request.requestId() != null) {
            MDC.put("correlationId", request.requestId());
        }
        MDC.put("eventType", request.dataType());
        if (request.userId() != null) {
            MDC.put("userId", request.userId());
        }
    }

    public void registerNewRequest(AmqpMessage message, ResponseHandler responseHandler) {
        Map<String, Object> properties = message.properties().getHeaders();
        String eventType = message.eventType();
        String ipAddress = (String) properties.get(IP_ADDRESS);
        String userAgent = (String) properties.get(USER_AGENT);
        String referer = (String) properties.get(REQUEST_REFERER);
        String requestUri = (String) properties.get(REQUEST_URI);
        String requestVia = (String) properties.get(REQUEST_VIA);
        String device = (String) properties.get(DEVICE);
        String userId = message.userId();
        String sessionId = message.sessionId();
        String request = message.body().copy().toString(StandardCharsets.UTF_8);
        registerNewRequest(
                new BackendRequest(message.correlationId(), eventType, Instant.now(), request, requestUri, ipAddress, userAgent,
                        referer, requestVia, device, userId, sessionId, responseHandler));

    }

}
