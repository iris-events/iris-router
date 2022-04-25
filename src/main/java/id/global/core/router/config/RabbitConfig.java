package id.global.core.router.config;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.reactive.messaging.rabbitmq.RabbitMQConnector;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;

@ApplicationScoped
public class RabbitConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    @Inject
    @Connector(value = "smallrye-rabbitmq")
    RabbitMQConnector connnector;

    /*
     * @Inject
     * Instance<RabbitMQOptions> options;
     */

    @Produces
    public RabbitMQClient createClient() {
        var options = createOptions();
        log.info("options: host: {}, port: {}, ssl:{}", options.getHost(),options.getPort(), options.isSsl());
        return RabbitMQClient.create(connnector.getVertx().getDelegate(), options);

    }

    @Produces
    //@Identifier("iris")
    @Default
    public RabbitMQOptions createOptions() {
        return new RabbitMQOptions().setHost(System.getProperty("rabbitmq-host", "localhost"))
                .setPort(Integer.parseInt(System.getProperty("rabbitmq-port", "5672")))
                .setUser(System.getProperty("rabbitmq-username", "guest"))
                .setPassword(System.getProperty("rabbitmq-password", "guest"))
                .setSsl(Boolean.getBoolean("rabbitmq-ssl"));

    }
}
