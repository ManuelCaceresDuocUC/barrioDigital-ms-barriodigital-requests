package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = MessagingPropertiesTest.TestConfig.class)
class MessagingPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(MessagingProperties.class)
    static class TestConfig { }

    @Autowired
    private MessagingProperties messaging;

    @Test
    void usaElExchangeYLasRoutingKeysDelCaso() {
        assertThat(messaging.exchange()).isEqualTo("cmd.direct");
        assertThat(messaging.routingKeys().email()).isEqualTo("email.send");
        assertThat(messaging.routingKeys().crew()).isEqualTo("crew.ticket");
        assertThat(messaging.routingKeys().certificate()).isEqualTo("certificate.gen");
    }
}