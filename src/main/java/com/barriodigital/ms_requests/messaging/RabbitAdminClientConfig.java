package com.barriodigital.ms_requests.messaging;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * QUÉ ES: configuración del cliente HTTP hacia rabbitadmin (el único servicio que habla con RabbitMQ).
 * Crea el RestClient con URL base y tiempos máximos de espera cortos (2 s conectar, 5 s leer) para que una
 * caída de rabbitadmin no retrase la respuesta al usuario.
 */
@Configuration
@EnableConfigurationProperties({ MessagingProperties.class, RabbitAdminProperties.class })
public class RabbitAdminClientConfig {

    @Bean
    RestClient rabbitAdminRestClient(RabbitAdminProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
    }
}
