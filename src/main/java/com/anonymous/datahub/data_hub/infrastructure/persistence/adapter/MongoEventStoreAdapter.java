package com.anonymous.datahub.data_hub.infrastructure.persistence.adapter;

import com.anonymous.datahub.data_hub.domain.model.EventPersistenceOutcome;
import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.model.SourceEventVolume;
import com.anonymous.datahub.data_hub.domain.port.EventStorePort;
import com.anonymous.datahub.data_hub.infrastructure.persistence.document.RawEventDocument;
import com.anonymous.datahub.data_hub.infrastructure.persistence.repository.RawEventMongoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class MongoEventStoreAdapter implements EventStorePort {

    private final RawEventMongoRepository rawEventMongoRepository;
    private final MongoTemplate mongoTemplate;

    public MongoEventStoreAdapter(
            RawEventMongoRepository rawEventMongoRepository,
            MongoTemplate mongoTemplate
    ) {
        this.rawEventMongoRepository = rawEventMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public EventPersistenceOutcome saveIfAbsent(IncomingEvent event) {
        RawEventDocument document = toDocument(event);
        try {
            rawEventMongoRepository.insert(document);
            return EventPersistenceOutcome.STORED;
        } catch (DuplicateKeyException ex) {
            return EventPersistenceOutcome.DUPLICATE;
        }
    }

    @Override
    public Optional<IncomingEvent> findByEventId(String eventId) {
        return rawEventMongoRepository.findByEventId(eventId).map(this::toDomain);
    }

    @Override
    public List<IncomingEvent> findAll() {
        return rawEventMongoRepository.findAllByOrderByReceivedAtDesc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public IncomingEvent updateByEventId(String eventId, IncomingEvent event) {
        RawEventDocument existing = rawEventMongoRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        existing.setSourceSystem(event.sourceSystem());
        existing.setPayload(event.payload());
        existing.setOccurredAt(event.occurredAt());
        existing.setReceivedAt(event.receivedAt());

        RawEventDocument updated = rawEventMongoRepository.save(existing);
        return toDomain(updated);
    }

    @Override
    public void deleteByEventId(String eventId) {
        rawEventMongoRepository.deleteByEventId(eventId);
    }

    @Override
    public long countReceivedBetween(Instant from, Instant to) {
        return rawEventMongoRepository.countByReceivedAtBetween(from, to);
    }

    @Override
    public List<SourceEventVolume> summarizeBySourceBetween(Instant from, Instant to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("receivedAt").gte(from).lte(to)),
                Aggregation.group("sourceSystem").count().as("total"),
                Aggregation.project("total").and("_id").as("sourceSystem")
        );

        AggregationResults<SourceVolumeDocument> results = mongoTemplate.aggregate(
                aggregation,
                "raw_event",
                SourceVolumeDocument.class
        );

        return results.getMappedResults()
                .stream()
                .map(item -> new SourceEventVolume(item.sourceSystem, item.total))
                .toList();
    }

    private RawEventDocument toDocument(IncomingEvent event) {
        RawEventDocument document = new RawEventDocument();
        document.setEventId(event.eventId());
        document.setSourceSystem(event.sourceSystem());
        document.setPayload(event.payload());
        document.setOccurredAt(event.occurredAt());
        document.setReceivedAt(event.receivedAt());
        return document;
    }

    private IncomingEvent toDomain(RawEventDocument document) {
        return new IncomingEvent(
                document.getEventId(),
                document.getSourceSystem(),
                document.getPayload(),
                document.getOccurredAt(),
                document.getReceivedAt()
        );
    }

    private static class SourceVolumeDocument {
        private String sourceSystem;
        private long total;
    }
}
