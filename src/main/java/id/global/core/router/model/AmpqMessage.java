package id.global.core.router.model;

import com.rabbitmq.client.AMQP;

public record AmpqMessage(byte[] body, AMQP.BasicProperties properties) {
}
