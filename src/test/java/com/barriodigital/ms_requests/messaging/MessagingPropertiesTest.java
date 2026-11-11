package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = MessagingPropertiesTest.TestConfig.class)
class MessagingPropertiesTest {

    @Configuration
    @EnableConfigurationProperties({ MessagingProperties.class, RabbitAdminProperties.class })
    static class TestConfig { }

    @Autowired
    private MessagingProperties messaging;

    @Autowired
    private RabbitAdminProperties rabbitAdmin;

    @Test
    void usaLasRoutingKeysDelCaso() {
        assertThat(messaging.routingKeys().email()).isEqualTo("email.send");
        assertThat(messaging.routingKeys().crew()).isEqualTo("crew.ticket");
        assertThat(messaging.routingKeys().certificate()).isEqualTo("certificate.gen");
    }

    @Test
    void elClienteDeRabbitadminTieneValoresPorDefecto() {
        assertThat(rabbitAdmin.url()).isEqualTo("http://localhost:8086");
        assertThat(rabbitAdmin.connectTimeout()).hasSeconds(2);
        assertThat(rabbitAdmin.readTimeout()).hasSeconds(5);
    }

    @Test
    void rechazaRoutingKeysVacias() {
        assertThatThrownBy(() -> new MessagingProperties.RoutingKeys(" ", "crew.ticket", "certificate.gen"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessagingProperties(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
