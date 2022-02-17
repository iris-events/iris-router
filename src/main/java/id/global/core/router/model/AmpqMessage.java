package id.global.core.router.model;

import static id.global.common.headers.amqp.MessagingHeaders.Message.CLIENT_TRACE_ID;
import static id.global.common.headers.amqp.MessagingHeaders.Message.CURRENT_SERVICE_ID;
import static id.global.common.headers.amqp.MessagingHeaders.Message.ROUTER;
import static id.global.common.headers.amqp.MessagingHeaders.Message.SESSION_ID;

import com.rabbitmq.client.AMQP;

public record AmpqMessage(byte[] body, AMQP.BasicProperties properties, String eventType) {

    public String userId() {
        return getStringHeader(properties, "userId");
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

    private String getStringHeader(AMQP.BasicProperties props, String name) {
        var r = props.getHeaders().get(name);
        if (r != null) {
            return r.toString();
        }
        return null;
    }
}
