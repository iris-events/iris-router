package id.global.core.router.model;

import static id.global.common.constants.iris.MessagingHeaders.Message.CLIENT_TRACE_ID;
import static id.global.common.constants.iris.MessagingHeaders.Message.CURRENT_SERVICE_ID;
import static id.global.common.constants.iris.MessagingHeaders.Message.ROUTER;
import static id.global.common.constants.iris.MessagingHeaders.Message.SESSION_ID;
import static id.global.common.constants.iris.MessagingHeaders.Message.SUBSCRIPTION_ID;
import static id.global.common.constants.iris.MessagingHeaders.Message.USER_ID;

import com.rabbitmq.client.BasicProperties;

import io.vertx.core.buffer.Buffer;

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
}
