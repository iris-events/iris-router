package id.global.core.router.consumer;

import java.util.List;

import id.global.core.router.model.ResponseMessageType;
import id.global.iris.common.constants.Exchanges;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RabbitMqSessionConsumer extends AbstractRabbitMqConsumer {

    @Override
    protected String getQueueName() {
        return Exchanges.SESSION.getValue();
    }

    @Override
    protected List<String> getQueueRoles() {
        return List.of();
    }

    @Override
    protected ResponseMessageType getSocketMessageType() {
        return ResponseMessageType.SESSION;
    }

}
