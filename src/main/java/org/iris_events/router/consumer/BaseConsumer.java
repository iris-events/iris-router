package org.iris_events.router.consumer;

import com.rabbitmq.client.AMQP;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.iris_events.common.MDCProperties;
import org.iris_events.router.model.AmqpMessage;
import org.iris_events.router.model.ResponseMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.CompletionStage;

import static org.iris_events.common.MessagingHeaders.Message.EVENT_TYPE;

public abstract class BaseConsumer {

    private static final Logger log = LoggerFactory.getLogger(BaseConsumer.class);

    /*@Inject
    RabbitMQClient client;*/

    public static void enrichMDC(final AmqpMessage m) {
        if (m.sessionId() != null)
            MDC.put(MDCProperties.SESSION_ID, m.sessionId());
        if (m.userId() != null)
            MDC.put(MDCProperties.USER_ID, m.userId());
        if (m.clientTraceId() != null)
            MDC.put(MDCProperties.CLIENT_TRACE_ID, m.clientTraceId());
        if (m.correlationId() != null)
            MDC.put(MDCProperties.CORRELATION_ID, m.correlationId());
        if (m.ipAddress() != null)
            MDC.put("ipAddress", m.ipAddress());
        if (m.userAgent() != null)
            MDC.put("userAgent", m.userAgent());
        if (m.userAgent() != null)
            MDC.put("device", m.deviceId());
        MDC.put(MDCProperties.EVENT_TYPE, m.eventType());
    }



    protected abstract ResponseMessageType getSocketMessageType();


    public abstract void onMessage(AmqpMessage message);


    public CompletionStage<Void> handleMessage(Message<byte[]> message) {
        var meta = message.getMetadata(IncomingRabbitMQMetadata.class).orElseThrow();
        var body = message.getPayload();
        meta.getCorrelationId().ifPresent(correlationId -> {
            MDC.put(MDCProperties.CORRELATION_ID, correlationId);
        });


        Object event = meta.getHeaders().get(EVENT_TYPE);
        if (event == null) {
            throw new RuntimeException("Required header '" + EVENT_TYPE + "' missing on message");
        }

        //todo properties
        AmqpMessage m = new AmqpMessage(Buffer.buffer(body),
                new AMQP.BasicProperties().builder()
                        .headers(meta.getHeaders())
                        .correlationId(meta.getCorrelationId().orElse(null))
                        .build(),
                event.toString());
        enrichMDC(m);
        onMessage(m);
        return message.ack();
    }
}
