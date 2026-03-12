package com.anonymous.datahub.data_hub.application.service;

import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.port.EventBusinessProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpEventBusinessProcessor implements EventBusinessProcessor {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventBusinessProcessor.class);

    @Override
    public void process(IncomingEvent event) {
        log.debug("Domain processing executed for eventId={}", event.eventId());
    }
}
