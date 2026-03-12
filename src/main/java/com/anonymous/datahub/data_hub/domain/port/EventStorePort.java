package com.anonymous.datahub.data_hub.domain.port;

import com.anonymous.datahub.data_hub.domain.model.EventPersistenceOutcome;
import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.model.SourceEventVolume;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventStorePort {

    EventPersistenceOutcome saveIfAbsent(IncomingEvent event);

    Optional<IncomingEvent> findByEventId(String eventId);

    List<IncomingEvent> findAll();

    IncomingEvent updateByEventId(String eventId, IncomingEvent event);

    void deleteByEventId(String eventId);

    long countReceivedBetween(Instant from, Instant to);

    List<SourceEventVolume> summarizeBySourceBetween(Instant from, Instant to);
}
