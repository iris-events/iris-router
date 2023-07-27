package org.iris_events.router.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.iris_events.router.model.ResponseMessageType;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class RabbitMqErrorConsumer extends AbstractRabbitMqConsumer {

    @Incoming("error")
    public CompletionStage<Void> consume(Message<byte[]> message) {
        return super.handleMessage(message);
    }

    @Override
    protected ResponseMessageType getSocketMessageType() {
        return ResponseMessageType.ERROR;
    }

}
