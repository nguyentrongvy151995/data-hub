package com.anonymous.datahub.data_hub.infrastructure.persistence.repository;

import com.anonymous.datahub.data_hub.infrastructure.persistence.document.RawEventDocument;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.mongodb.uri=mongodb://localhost:27017/data_hub_test",
        "spring.data.mongodb.auto-index-creation=true",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "app.kafka.listener.enabled=false",
        "app.kafka.producer.enabled=false"
})
class RawEventMongoRepositoryIntegrationTest {

    @Autowired
    private RawEventMongoRepository rawEventMongoRepository;

    @BeforeEach
    void cleanData() {
        assumeMongoIsAvailable();
        rawEventMongoRepository.deleteAll();
    }

    @Test
    void shouldFindByEventIdAfterInsert() {
        RawEventDocument saved = rawEventMongoRepository.insert(document("evt-001", Instant.parse("2026-03-13T10:00:00Z")));

        Optional<RawEventDocument> found = rawEventMongoRepository.findByEventId(saved.getEventId());

        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo("evt-001");
    }

    @Test
    void shouldCountDocumentsByUpdatedAtBetween() {
        rawEventMongoRepository.insert(document("evt-001", Instant.parse("2026-03-13T10:00:00Z")));
        rawEventMongoRepository.insert(document("evt-002", Instant.parse("2026-03-14T10:00:00Z")));

        long count = rawEventMongoRepository.countByUpdatedAtBetween(
                Instant.parse("2026-03-13T00:00:00Z"),
                Instant.parse("2026-03-13T23:59:59Z")
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldDeleteByEventId() {
        rawEventMongoRepository.insert(document("evt-003", Instant.parse("2026-03-13T10:00:00Z")));

        rawEventMongoRepository.deleteByEventId("evt-003");

        assertThat(rawEventMongoRepository.findByEventId("evt-003")).isEmpty();
    }

    @Test
    void shouldRejectDuplicateEventIdWhenUniqueIndexExists() {
        rawEventMongoRepository.insert(document("evt-dup", Instant.parse("2026-03-13T10:00:00Z")));

        assertThatThrownBy(() -> rawEventMongoRepository.insert(document("evt-dup", Instant.parse("2026-03-13T11:00:00Z"))))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private void assumeMongoIsAvailable() {
        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "MongoDB is not available on localhost:27017: " + ex.getMessage());
        }
    }

    private RawEventDocument document(String eventId, Instant updatedAt) {
        RawEventDocument document = new RawEventDocument();
        document.setEventId(eventId);
        document.setEventType("BET");
        document.setSourceSystem("systemA");
        document.setStatus("SUCCESS");
        document.setPayload("{\"amount\":100}");
        document.setCreatedAt(Instant.parse("2026-03-13T09:00:00Z"));
        document.setUpdatedAt(updatedAt);
        return document;
    }
}
