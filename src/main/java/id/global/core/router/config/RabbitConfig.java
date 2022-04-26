package id.global.core.router.config;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.arc.profile.UnlessBuildProfile;
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
    @UnlessBuildProfile("prod")
    @ApplicationScoped
    public RabbitMQOptions createDevOptions(Config config) {
        var host = config.getValue("rabbitmq-host", String.class);
        var port = config.getValue("rabbitmq-port", Integer.class);
        var username = config.getValue("rabbitmq-username", String.class);
        var password = config.getValue("rabbitmq-password", String.class);
        var ssl = config.getOptionalValue("rabbitmq-ssl", Boolean.class).orElse(false);
        log.info("host: {}, port: {}, user: {}, pass: {}, ssl: {}", host, port, username, password, ssl);

        var options = new RabbitMQOptions().setHost(host)
                .setPort(port)
                .setUser(username)
                .setPassword(password)
                .setSsl(ssl);
        log.info("non prod options: host: {}, port: {}, ssl: {}", options.getHost(), options.getPort(), options.isSsl());
        return options;

    }

    @Produces
    @DefaultBean
    @IfBuildProfile("prod")
    @ApplicationScoped
    public RabbitMQOptions createProdOptions(Config config) {
        var rabbitUrl = config.getValue("quarkus.iris.rabbitmq.url", String.class);
        var rabbitUsername = config.getValue("quarkus.iris.rabbitmq.username", String.class);
        var rabbitPassword = config.getValue("quarkus.iris.rabbitmq.password", String.class);
        log.info("need to configure prod, url: {}", rabbitUrl);
        return new RabbitMQOptions().setTrustAll(false)
                .setAutomaticRecoveryEnabled(true)
                .setUser(rabbitUsername)
                .setPassword(rabbitPassword)
                .setUri(rabbitUrl);

    }
}
