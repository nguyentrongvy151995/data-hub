package com.anonymous.datahub.data_hub.infrastructure.persistence.repository;

import com.anonymous.datahub.data_hub.infrastructure.persistence.document.RawEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RawEventMongoRepository extends MongoRepository<RawEventDocument, String> {

    Optional<RawEventDocument> findByEventId(String eventId);

    List<RawEventDocument> findAllByOrderByReceivedAtDesc();

    void deleteByEventId(String eventId);

    long countByReceivedAtBetween(Instant from, Instant to);
}
