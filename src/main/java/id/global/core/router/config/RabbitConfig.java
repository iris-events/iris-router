package id.global.core.router.config;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.DefaultBean;
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
    public RabbitMQClient createClient(RabbitMQOptions options) {
        //var options = createOptions();
        //log.info("options: host: {}, port: {}, ssl:{}", options.getHost(), options.getPort(), options.isSsl());
        return RabbitMQClient.create(connnector.getVertx().getDelegate(), options);

    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public RabbitMQOptions createDevOptions(Config config) {
        var host = config.getValue("rabbitmq-host", String.class);
        var vhost = config.getOptionalValue("rabbitmq-virtual-host", String.class).orElse("/");
        var port = getPort(config);
        var ssl = isSsl(config);
        var username = config.getValue("rabbitmq-username", String.class);
        var password = config.getValue("rabbitmq-password", String.class);
        var options = new RabbitMQOptions()
                .setHost(host)
                .setVirtualHost(vhost)
                .setPort(port)
                .setUser(username)
                .setPassword(password)
                .setSsl(ssl);

        log.info("Rabbit options: host: {}, port: {}, ssl: {}", options.getHost(), options.getPort(), options.isSsl());

        return options;
    }

    public int getPort(final Config config) {
        final var protocol = config.getOptionalValue("rabbitmq-protocol", String.class);
        final var port = config.getOptionalValue("rabbitmq-port", Integer.class);
        return protocol.map(p -> switch (p) {
            case "amqp" -> port.orElse(5672);
            case "amqps" -> port.orElse(5671);
            default -> throw new IllegalStateException("Unknown protocol value: " + p);
        }).orElseGet(() -> port.orElse(5672));
    }

    public boolean isSsl(final Config config) {
        final var protocol = config.getOptionalValue("rabbitmq-protocol", String.class);
        final var ssl = config.getOptionalValue("rabbitmq-ssl", Boolean.class);
        return protocol.map(p -> switch (p) {
            case "amqp" -> ssl.orElse(false);
            case "amqps" -> ssl.orElse(true);
            default -> throw new IllegalStateException("Unknown protocol value: " + p);
        }).orElseGet(() -> ssl.orElse(false));
    }
}
