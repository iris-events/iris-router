package id.global.core.router.model;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.events.RouterEvent;
import io.vertx.core.buffer.Buffer;

public final class RawMessage {
    private final String message;

    public RawMessage(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    interface RawMessageEventName {
        RawMessageClientTraceId setEventName(String eventName);
    }

    interface RawMessageClientTraceId {
        RawMessageSubscriptionId setClientTraceId(String clientTraceId);
    }

    interface RawMessageSubscriptionId {
        RawMessagePayload setSubscriptionId(String subscriptionId);
    }

    interface RawMessagePayload {
        RawMessageCreator setPayloadFromBuffer(Buffer buffer);

        RawMessageCreator setPayloadFromEvent(RouterEvent routerEvent);
    }

    interface RawMessageCreator {
        RawMessage build();
    }

    public static class RawMessageBuilder
            implements RawMessageEventName, RawMessageClientTraceId, RawMessageSubscriptionId, RawMessagePayload,
            RawMessageCreator {

        private static final Logger log = LoggerFactory.getLogger(RawMessageBuilder.class);
        private static final String EVENT_FIELD = "event";
        private static final String PAYLOAD_FIELD = "payload";
        private static final String CLIENT_TRACE_ID_FIELD = "client_trace_id";
        private static final String SUBSCRIPTION_ID_FIELD = "subscription_id";

        private final ObjectMapper objectMapper;

        private String eventName;
        private String clientTraceId;
        private String subscriptionId;
        private RouterEvent routerEventPayload;
        private Buffer bufferPayload;

        public RawMessageBuilder(final ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public static RawMessageEventName getInstance(final ObjectMapper objectMapper) {
            return new RawMessageBuilder(objectMapper);
        }

        @Override
        public RawMessageClientTraceId setEventName(final String eventName) {
            this.eventName = eventName;
            return this;
        }

        @Override
        public RawMessageSubscriptionId setClientTraceId(final String clientTraceId) {
            this.clientTraceId = clientTraceId;
            return this;
        }

        @Override
        public RawMessagePayload setSubscriptionId(final String subscriptionId) {
            this.subscriptionId = subscriptionId;
            return this;
        }

        @Override
        public RawMessageCreator setPayloadFromBuffer(final Buffer buffer) {
            this.bufferPayload = buffer;
            return this;
        }

        @Override
        public RawMessageCreator setPayloadFromEvent(final RouterEvent routerEvent) {
            this.routerEventPayload = routerEvent;
            return this;
        }

        @Override
        public RawMessage build() {
            final var writer = new StringWriter();
            try {
                final var generator = objectMapper.getFactory().createGenerator(writer);
                generator.writeStartObject();
                generator.writeStringField(EVENT_FIELD, this.eventName);
                if (clientTraceId != null) {
                    generator.writeStringField(CLIENT_TRACE_ID_FIELD, clientTraceId);
                }
                if (subscriptionId != null) {
                    generator.writeStringField(SUBSCRIPTION_ID_FIELD, subscriptionId);
                }
                if (routerEventPayload != null) {
                    generator.writeFieldName(PAYLOAD_FIELD);
                    generator.writeObject(routerEventPayload);
                } else if (bufferPayload != null) {
                    generator.writeFieldName(PAYLOAD_FIELD);
                    generator.writeRawValue(bufferPayload.toString(StandardCharsets.UTF_8));
                }
                generator.writeEndObject();
                generator.close();
            } catch (IOException e) {
                log.error("Could not convert json object", e);
                throw new RuntimeException(e);
            }

            final var message = writer.toString();
            return new RawMessage(message);
        }
    }

}
