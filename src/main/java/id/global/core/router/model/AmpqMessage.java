package id.global.core.router.model;

import com.rabbitmq.client.AMQP;

public record AmpqMessage(byte[] body, AMQP.BasicProperties properties, String eventType) {

    public String userId() {
        return getStringHeader(properties, "userId");
    }

    public String correlationId() {
        return properties.getCorrelationId();
    }

    public String clientTraceId() {
        return getStringHeader(properties, "clientTraceId");
    }

    public String sessionId() {
        return getStringHeader(properties, "sessionId");
    }

    public String currentServiceId() {
        return getStringHeader(properties, "currentServiceId");
    }

    public String routerId() {
        return getStringHeader(properties, "router");
    }

    private String getStringHeader(AMQP.BasicProperties props, String name) {
        var r = props.getHeaders().get(name);
        if (r != null) {
            return r.toString();
        }
        return null;
    }
}
