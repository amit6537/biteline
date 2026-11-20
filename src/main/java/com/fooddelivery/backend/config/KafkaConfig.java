package com.fooddelivery.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConfig — Standard, production-grade Spring Kafka configuration.
 *
 * CONCEPT: Explicit JSON Strings for Maximum Compatibility
 * ──────────────────────────────────────────────────────────
 * 1. Uses native, non-deprecated StringSerializer and StringDeserializer.
 * 2. OrderEventProducer explicitly serializes event records to JSON using Jackson ObjectMapper.
 * 3. Consumers receive the JSON string and deserialize to typed Java records.
 * 4. This avoids all version-specific framework serializer deprecation issues
 *    and gives complete control over serialization errors.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ── Topic declarations ────────────────────────────────────
    public static final String TOPIC_ORDER_PLACED         = "order.placed";
    public static final String TOPIC_PAYMENT_SUCCESS      = "payment.success";
    public static final String TOPIC_ORDER_STATUS_CHANGED = "order.status.changed";
    public static final String TOPIC_ORDER_READY          = "order.ready";
    public static final String TOPIC_ORDER_DELIVERED      = "order.delivered";

    @Bean public NewTopic orderPlacedTopic()         { return new NewTopic(TOPIC_ORDER_PLACED, 3, (short) 1); }
    @Bean public NewTopic paymentSuccessTopic()      { return new NewTopic(TOPIC_PAYMENT_SUCCESS, 3, (short) 1); }
    @Bean public NewTopic orderStatusChangedTopic()  { return new NewTopic(TOPIC_ORDER_STATUS_CHANGED, 3, (short) 1); }
    @Bean public NewTopic orderReadyTopic()          { return new NewTopic(TOPIC_ORDER_READY, 3, (short) 1); }
    @Bean public NewTopic orderDeliveredTopic()      { return new NewTopic(TOPIC_ORDER_DELIVERED, 3, (short) 1); }

    // ── Jackson ObjectMapper ──────────────────────────────────
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ── Producer configuration (String, String) ───────────────
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer configuration (String, String) ───────────────
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        return factory;
    }
}
