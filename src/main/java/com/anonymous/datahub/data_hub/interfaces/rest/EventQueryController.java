package com.anonymous.datahub.data_hub.interfaces.rest;

import com.anonymous.datahub.data_hub.application.usecase.QueryEventUseCase;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.EventResponse;
import com.anonymous.datahub.data_hub.interfaces.rest.mapper.RestResponseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventQueryController {

    private final QueryEventUseCase queryEventUseCase;
    private final RestResponseMapper restResponseMapper;

    public EventQueryController(QueryEventUseCase queryEventUseCase, RestResponseMapper restResponseMapper) {
        this.queryEventUseCase = queryEventUseCase;
        this.restResponseMapper = restResponseMapper;
    }

    @GetMapping
    public List<EventResponse> getAll() {
        return queryEventUseCase.getAll()
                .stream()
                .map(restResponseMapper::toEventResponse)
                .toList();
    }

    @GetMapping("/{eventId}")
    public EventResponse getByEventId(@PathVariable String eventId) {
        return restResponseMapper.toEventResponse(queryEventUseCase.getByEventId(eventId));
    }
}
