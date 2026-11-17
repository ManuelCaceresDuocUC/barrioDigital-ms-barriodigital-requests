package com.barriodigital.ms_requests.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * QUÉ ES: configuración del cliente HTTP (RestClient) que usa CatalogClient para llamar al catálogo.
 * Define la URL base y los tiempos máximos de espera para no quedar colgado si el catálogo no responde.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogClientConfig {

    @Bean
    RestClient catalogRestClient(CatalogProperties properties) {
        // Tiempo máximo para establecer la conexión.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // Tiempo máximo esperando la respuesta, ya conectado.
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                // Con baseUrl, CatalogClient solo indica la ruta relativa.
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
    }
}
