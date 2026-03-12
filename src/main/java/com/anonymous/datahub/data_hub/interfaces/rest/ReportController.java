package com.anonymous.datahub.data_hub.interfaces.rest;

import com.anonymous.datahub.data_hub.application.usecase.QueryEventUseCase;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.IngestionSummaryResponse;
import com.anonymous.datahub.data_hub.interfaces.rest.mapper.RestResponseMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final QueryEventUseCase queryEventUseCase;
    private final RestResponseMapper restResponseMapper;

    public ReportController(QueryEventUseCase queryEventUseCase, RestResponseMapper restResponseMapper) {
        this.queryEventUseCase = queryEventUseCase;
        this.restResponseMapper = restResponseMapper;
    }

    @GetMapping("/ingestion-summary")
    public IngestionSummaryResponse getIngestionSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Instant toTime = to == null ? Instant.now() : to;
        Instant fromTime = from == null ? toTime.minus(24, ChronoUnit.HOURS) : from;

        return restResponseMapper.toSummaryResponse(queryEventUseCase.getSummary(fromTime, toTime));
    }
}
