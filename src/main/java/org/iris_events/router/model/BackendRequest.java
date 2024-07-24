package org.iris_events.router.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.buffer.Buffer;

/**
 * @author Tomaz Cerar
 */
@RegisterForReflection
public record BackendRequest(String requestId, String dataType, Instant created,
        String requestBody, String ipAddress,
        String userAgent, String referer, String requestVia,
        String device, String userId, String sessionId,
        ResponseHandler responseHandler) {

    private static final Pattern SENSITIVE_PATTERN = Pattern
            .compile("\"(?:email|password|authentication|jwtToken|token)\":\"([^\"]+)\"");

    public BackendRequest(String requestId, String dataType, Instant created, String requestBody,
            String ipAddress, String userAgent, String referer, String requestVia, String device, String userId,
            String sessionId, ResponseHandler responseHandler) {
        this.requestId = requestId;
        this.dataType = dataType;
        this.created = created;
        this.requestBody = sanitizeBody(shortenRequestBody(requestBody));
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.referer = referer;
        this.requestVia = requestVia;
        this.device = device;
        this.userId = userId;
        this.sessionId = sessionId;
        this.responseHandler = responseHandler;
    }

    private static String shortenRequestBody(String requestBody) {
        if (requestBody.length() > 400) {
            return requestBody.substring(0, 400) + "...";
        }
        return requestBody;
    }

    public static String sanitizeBody(byte[] body) {
        return sanitizeBody(new String(body, StandardCharsets.UTF_8));
    }

    public static String sanitizeBody(Buffer body) {
        return sanitizeBody(body.toString(StandardCharsets.UTF_8));
    }

    public static String sanitizeBody(String body) {

        return SENSITIVE_PATTERN.matcher(body).replaceAll(r -> r.group().replace(r.group(1), "**"));
    }

    public static void main(String[] args) {
        System.out.println(sanitizeBody(
                "{\"deviceId\":\"f8b1ad2a-d3f9-4584-8263-626f8bf8b059\",\"email\":\"test@example.com\",\"password\":\"blah\", \"authentication\":\"blah\", \"jwtToken\":\"blah\"}"));
    }
}
