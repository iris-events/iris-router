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

    private static void enrichMDC(final AmqpMessage m) {
        if (m.sessionId() != null)
            MDC.put(MDCProperties.SESSION_ID, m.sessionId());
        if (m.userId() != null)
            MDC.put(MDCProperties.USER_ID, m.userId());
        if (m.clientTraceId() != null)
            MDC.put(MDCProperties.CLIENT_TRACE_ID, m.clientTraceId());
        if (m.correlationId() != null)
            MDC.put(MDCProperties.CORRELATION_ID, m.correlationId());
        MDC.put(MDCProperties.EVENT_TYPE, m.eventType());
    }

    private static Throwable findRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }


    protected abstract ResponseMessageType getSocketMessageType();


    public abstract void onMessage(AmqpMessage message);


    private void handleMessage(RabbitMQMessage message) {
        try {
            var properties = message.properties();
            var body = message.body();
            final var correlationId = properties.getCorrelationId();
            if (correlationId != null) {
                MDC.put(MDCProperties.CORRELATION_ID, correlationId);
            }

            Object event = properties.getHeaders().get(EVENT_TYPE);
            if (event == null) {
                throw new RuntimeException("Required header '" + EVENT_TYPE + "' missing on message");
            }

            AmqpMessage m = new AmqpMessage(body, properties, event.toString());
            enrichMDC(m);
            onMessage(m);
        } catch (Exception e) {
            log.warn("Error handling message", e);
        } finally {
            MDC.clear();
        }
    }

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
