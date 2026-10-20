package com.barriodigital.ms_requests.messaging;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitProducerConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> log.error(
                "Mensaje sin ruta: exchange={}, routingKey={}, motivo={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("El broker rechazó el mensaje {}: {}", correlation, cause);
            }
        });
        return template;
    }
}