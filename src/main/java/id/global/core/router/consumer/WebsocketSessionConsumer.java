package id.global.core.router.consumer;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.core.router.model.ResponseMessageType;

@ApplicationScoped
public class WebsocketSessionConsumer extends AbstractWebSocketConsumer {
    private static final Logger log = LoggerFactory.getLogger(AbstractWebSocketConsumer.class);

    @Override
    protected String getQueueName() {
        return "session";
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
