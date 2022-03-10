package id.global.core.router.config;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import io.smallrye.reactive.messaging.rabbitmq.RabbitMQConnector;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;

@ApplicationScoped
public class RabbitConfig {

    @Inject
    @Connector(value = "smallrye-rabbitmq")
    RabbitMQConnector connnector;

    @Inject
    Instance<RabbitMQOptions> options;

    @Produces
    public RabbitMQClient createClient() {
        RabbitMQOptions options = new RabbitMQOptions().setHost(System.getProperty("rabbitmq-host", "localhost"))
                .setPort(Integer.parseInt(System.getProperty("rabbitmq-port", "5672")))
                .setUser(System.getProperty("rabbitmq-username", "guest"))
                .setPassword(System.getProperty("rabbitmq-password", "guest"));
        return RabbitMQClient.create(connnector.getVertx().getDelegate(), options);

    }
}
