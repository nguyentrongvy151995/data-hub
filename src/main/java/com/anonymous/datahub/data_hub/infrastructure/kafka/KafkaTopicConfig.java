package com.anonymous.datahub.data_hub.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic rawEventsTopic(
            @Value("${app.kafka.topic.raw-events}") String topicName,
            @Value("${app.kafka.topic.raw-events.partitions:3}") int partitions,
            @Value("${app.kafka.topic.raw-events.replicas:1}") short replicas
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic rawEventsDltTopic(
            @Value("${app.kafka.topic.raw-events-dlt:${app.kafka.topic.raw-events}.DLT}") String topicName,
            @Value("${app.kafka.topic.raw-events.partitions:3}") int partitions,
            @Value("${app.kafka.topic.raw-events.replicas:1}") short replicas
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
