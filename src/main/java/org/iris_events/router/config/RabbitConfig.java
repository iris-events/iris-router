package org.iris_events.router.config;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.reactive.messaging.rabbitmq.RabbitMQConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RabbitConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    @Inject
    @Connector(value = "smallrye-rabbitmq")
    RabbitMQConnector connnector;
    @Inject
    SmallRyeConfig config;

}
