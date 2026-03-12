package com.anonymous.datahub.data_hub.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> manualAckKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Value("${spring.kafka.listener.concurrency:4}") int concurrency
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(1_500L);

        factory.getContainerProperties().getKafkaConsumerProperties().put(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                "500"
        );
        return factory;
    }
}
