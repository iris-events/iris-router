package id.global.core.router.consumer;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import id.global.core.router.model.ResponseMessageType;

@ApplicationScoped
public class WebsocketErrorConsumer extends AbstractWebSocketConsumer {

    @Override
    protected String getQueueName() {
        return "error";
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
