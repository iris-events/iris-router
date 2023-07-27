package org.iris_events.router.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.iris_events.annotations.Message;
import org.iris_events.asyncapi.parsers.ExchangeParser;
import org.iris_events.router.model.AmqpMessage;
import org.iris_events.router.model.RequestWrapper;
import org.iris_events.router.model.UserSession;
import org.iris_events.router.model.sub.SessionClosed;
import org.iris_events.router.model.sub.SubscribeInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomaz Cerar
 */
@ApplicationScoped
public class BackendService {
    private static final Logger log = LoggerFactory.getLogger(BackendService.class);

    private final WebsocketRegistry websocketRegistry;


    @Inject
    @Channel("frontend")
    Emitter<Buffer> frontendPublisher;

    @Inject
    @Channel("session-closed")
    Emitter<Buffer> sessionClosedPublisher;

    @Inject
    @Channel("subscribe-internal")
    Emitter<Buffer> subscribeInternalPublisher;


    @Inject
    ObjectMapper objectMapper;

    public BackendService(WebsocketRegistry websocketRegistry) {
        this.websocketRegistry = websocketRegistry;

    }


    public void sendToFrontendQueue(AmqpMessage message) {
        var meta = message.toMetadata()
                .withRoutingKey(message.eventType())
                .build();
        var m = org.eclipse.microprofile.reactive.messaging.Message.of(message.body(), Metadata.of(meta));
        frontendPublisher.send(m);
    }

    public void sendFrontendEvent(UserSession userSession, RequestWrapper requestWrapper) {
        final var message = userSession.createBackendRequest(requestWrapper);
        websocketRegistry.registerRequest(message);
        sendToFrontendQueue(message);
    }

    public void sendInternalEventToBackend(AmqpMessage message, Emitter<Buffer> emitter) {
        websocketRegistry.registerRequest(message);
        var meta = message.toMetadata()
                .build();
        var m = org.eclipse.microprofile.reactive.messaging.Message.of(message.body(), Metadata.of(meta));
        emitter.send(m);
    }

    private void sendInternalEvent(final UserSession userSession, final String clientTraceId, final Object message, Emitter<Buffer> emitter) {
        final var messageAnnotation = message.getClass().getAnnotation(Message.class);
        final var name = ExchangeParser.getFromAnnotationClass(messageAnnotation);
        final var msg = new RequestWrapper(name, clientTraceId, objectMapper.valueToTree(message));
        final var amqpMessage = userSession.createBackendRequest(msg);
        sendInternalEventToBackend(amqpMessage, emitter);
    }

    public void sendInternalEvent(final UserSession userSession, final String clientTraceId, final SessionClosed message) {
        sendInternalEvent(userSession, clientTraceId, message, sessionClosedPublisher);
    }

    public void sendInternalEvent(final UserSession userSession, final String clientTraceId, final SubscribeInternal message) {
        sendInternalEvent(userSession, clientTraceId, message, subscribeInternalPublisher);
    }
}
