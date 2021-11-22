package id.global.core.router.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.core.router.model.AmpqMessage;
import id.global.core.router.model.BackendRequest;
import id.global.core.router.model.ResponseHandler;
import id.global.core.router.model.ResponseMessageType;
import io.quarkus.scheduler.Scheduled;

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
    private final int requestTimeLogThreshold = 200000; // 200ms

    public RequestRegistry() {

    }

    private void registerNewRequest(BackendRequest request) {
        requestLogger.info("registering request {}", request);
        requests.put(request.requestId(), request);
    }

    public BackendRequest getActiveRequest(String requestId) {
        return requests.get(requestId);
    }

    public boolean isRequestValid(String requestId) {
        return requestId != null && requests.containsKey(requestId);
    }

    public void publishResponse(ResponseMessageType messageType, String correlationId, String clientTraceId, String userId,
            String sessionId, String dataType, String response) {
        BackendRequest request = requests.get(correlationId);
        if (request != null) {
            ResponseHandler handler = request.responseHandler();

            Duration duration = Duration.between(request.created(), Instant.now());
            if (duration.toMillis() >= requestTimeLogThreshold) {
                requestLogger.info("Request for '{}', params: {}, response event: {} took {} ms", request.dataType(),
                        request.requestBody(), dataType, duration.toMillis());
            }
            handler.handle(messageType, clientTraceId, userId, sessionId, dataType, response);
            requests.remove(correlationId);
        } else {
            requestErrorLogger.warn(
                    "could not properly handle message, request id no longer active, requestId: {}, clientTraceId: {}, userId: {}, event: {}",
                    correlationId, clientTraceId, userId, dataType);
        }

    }

    @Scheduled(every = "10s", delay = 1)
    public void checkForExpiredRequests() {
        final Instant expireTime = Instant.now().minus(Duration.ofSeconds(30));
        requests.values().forEach(n -> {
            if (n.created().isBefore(expireTime)) {
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

    public void registerNewRequest(String requestId, AmpqMessage message, ResponseHandler responseHandler) {
        Map<String, Object> properties = message.properties().getHeaders();
        String eventType = (String) properties.get("event");
        String ipAddress = (String) properties.get("ipAddress");
        String userAgent = (String) properties.get("userAgent");
        String referer = (String) properties.get("X-Request-Referer");
        String requestUri = (String) properties.get("X-Request-URI");
        String requestVia = (String) properties.get("X-Request-Via");
        String device = (String) properties.get("device");
        String userId = (String) properties.get("userId");
        String sessionId = (String) properties.get("sessionId");
        String request = new String(message.body(), StandardCharsets.UTF_8);
        registerNewRequest(new BackendRequest(requestId, eventType, Instant.now(), request, requestUri, ipAddress, userAgent,
                referer, requestVia, device, userId, sessionId, responseHandler));

    }

}
