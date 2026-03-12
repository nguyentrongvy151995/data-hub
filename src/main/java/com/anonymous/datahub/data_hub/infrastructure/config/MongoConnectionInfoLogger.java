package com.anonymous.datahub.data_hub.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.core.env.Environment;

@Configuration
public class MongoConnectionInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(MongoConnectionInfoLogger.class);

    @Bean
    public ApplicationRunner logMongoDatabaseName(MongoTemplate mongoTemplate, Environment environment) {
        return args -> {
            String uri = environment.getProperty("spring.mongodb.uri");
            if (uri == null) {
                uri = environment.getProperty("spring.data.mongodb.uri", "(not set)");
            }
            log.info("spring.mongodb.uri (runtime): {}", maskCredentials(uri));
            log.info("Mongo database in use: {}", mongoTemplate.getDb().getName());
        };
    }

    private String maskCredentials(String uri) {
        return uri.replaceAll("(?<=mongodb://)[^:@/]+:[^@/]+@", "***:***@");
    }
}
