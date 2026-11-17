package com.barriodigital.ms_requests.streaming;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * QUÉ ES: configuración del PRODUCTOR de Kafka. Crea el cliente con garantías de entrega y decide si se
 * usa el stream real o el "no-op" según barriodigital.kafka.enabled.
 */
@Configuration
@EnableConfigurationProperties(KafkaStreamProperties.class)
public class KafkaStreamConfig {

    // El productor solo se crea si enabled=true (o si falta la propiedad). destroyMethod = "close": al apagar
    // la app se cierra el productor y se vacían los envíos pendientes.
    @Bean(destroyMethod = "close")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "barriodigital.kafka.enabled", havingValue = "true", matchIfMissing = true)
    Producer<String, String> kafkaProducer(KafkaStreamProperties properties) {
        int timeoutMs = (int) properties.sendTimeout().toMillis();
        Map<String, Object> config = new HashMap<>();
        // Dirección de los brokers (ec2-kafka, IP privada).
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "ms-requests");
        // Clave y valor se envían como texto (el valor es el JSON ya armado).
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Sin pérdidas ni duplicados por reintento: espera a las réplicas en sincronía (min ISR 2 del clúster).
        // acks=all: el líder confirma solo cuando las réplicas sincronizadas ya lo tienen.
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Idempotencia: si el productor reintenta un envío, Kafka detecta y descarta el duplicado.
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Tiempos máximos (acotados) para no dejar tareas colgadas cuando Kafka no responde.
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMs);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.min(timeoutMs, 3_000));
        // Con Kafka caído, send() espera como máximo esto por los metadatos antes de fallar.
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, Math.min(timeoutMs, 2_000));
        return new KafkaProducer<>(config);
    }

    // Elige la implementación: con productor disponible -> KafkaEventStream; sin él (Kafka apagado) -> NoOp.
    // ObjectProvider permite pedir el productor "si existe" sin que falle el arranque cuando no está.
    @Bean
    EventStream eventStream(org.springframework.beans.factory.ObjectProvider<Producer<String, String>> producer) {
        Producer<String, String> available = producer.getIfAvailable();
        return available == null ? new NoOpEventStream() : new KafkaEventStream(available, JsonMapper.builder().build());
    }
}
