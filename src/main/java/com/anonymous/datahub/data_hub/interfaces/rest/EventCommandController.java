package com.anonymous.datahub.data_hub.interfaces.rest;

import com.anonymous.datahub.data_hub.application.usecase.ManageEventUseCase;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.CreateEventRequest;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.EventResponse;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.UpdateEventRequest;
import com.anonymous.datahub.data_hub.interfaces.rest.mapper.RestRequestMapper;
import com.anonymous.datahub.data_hub.interfaces.rest.mapper.RestResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventCommandController {

    private final ManageEventUseCase manageEventUseCase;
    private final RestRequestMapper restRequestMapper;
    private final RestResponseMapper restResponseMapper;

    public EventCommandController(
            ManageEventUseCase manageEventUseCase,
            RestRequestMapper restRequestMapper,
            RestResponseMapper restResponseMapper
    ) {
        this.manageEventUseCase = manageEventUseCase;
        this.restRequestMapper = restRequestMapper;
        this.restResponseMapper = restResponseMapper;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = restResponseMapper.toEventResponse(
                manageEventUseCase.create(restRequestMapper.toCreateEventDto(request))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{eventId}")
    public EventResponse update(@PathVariable String eventId, @Valid @RequestBody UpdateEventRequest request) {
        return restResponseMapper.toEventResponse(
                manageEventUseCase.update(eventId, restRequestMapper.toUpdateEventDto(request))
        );
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable String eventId) {
        manageEventUseCase.delete(eventId);
        return ResponseEntity.noContent().build();
    }
}
