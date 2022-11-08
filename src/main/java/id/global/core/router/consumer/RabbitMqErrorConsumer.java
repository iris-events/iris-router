package id.global.core.router.consumer;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import id.global.core.router.model.ResponseMessageType;
import id.global.iris.common.constants.Queues;

@ApplicationScoped
public class RabbitMqErrorConsumer extends AbstractRabbitMqConsumer {

    @Override
    protected String getQueueName() {
        return Queues.ERROR.getValue();
    }

    @Override
    protected List<String> getQueueRoles() {
        return List.of();
    }

    @Override
    protected ResponseMessageType getSocketMessageType() {
        return ResponseMessageType.ERROR;
    }

}
