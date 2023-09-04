package org.iris_events.router.model;

import com.rabbitmq.client.BasicProperties;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import io.vertx.core.buffer.Buffer;

import static org.iris_events.common.MessagingHeaders.Message.*;

public record AmqpMessage(Buffer body, BasicProperties properties, String eventType) {

    public String userId() {
        return getStringHeader(properties, USER_ID);
    }

    public String correlationId() {
        return properties.getCorrelationId();
    }

    public String clientTraceId() {
        return getStringHeader(properties, CLIENT_TRACE_ID);
    }

    public String sessionId() {
        return getStringHeader(properties, SESSION_ID);
    }

    public String currentServiceId() {
        return getStringHeader(properties, CURRENT_SERVICE_ID);
    }
    public String originServiceId() {
        return getStringHeader(properties, ORIGIN_SERVICE_ID);
    }

    public String routerId() {
        return getStringHeader(properties, ROUTER);
    }

    public String subscriptionId() {
        return getStringHeader(properties, SUBSCRIPTION_ID);
    }

    private String getStringHeader(BasicProperties props, String name) {
        var r = props.getHeaders().get(name);
        if (r != null) {
            return r.toString();
        }
        return null;
    }

    public OutgoingRabbitMQMetadata.Builder toMetadata() {
        return OutgoingRabbitMQMetadata
                .builder()
                .withCorrelationId(properties().getCorrelationId())
                .withAppId(properties().getAppId())
                .withMessageId(properties().getMessageId())
                .withUserId(properties().getUserId())
                .withContentEncoding(properties().getContentEncoding())
                .withContentType(properties().getContentType())
                .withExpiration(properties().getExpiration())
                .withReplyTo(properties().getReplyTo())
                .withType(properties().getType())
                .withDeliveryMode(properties().getDeliveryMode())
                .withPriority(properties().getPriority())
                .withHeaders(properties().getHeaders());

    }
}
