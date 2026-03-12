package com.anonymous.datahub.data_hub.domain.port;

import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;

public interface EventBusinessProcessor {

    void process(IncomingEvent event);
}
