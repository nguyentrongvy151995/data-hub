package com.anonymous.datahub.data_hub.interfaces.rest;

import com.anonymous.datahub.data_hub.application.usecase.CreatePersonUseCase;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.CreatePersonRequest;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.PersonResponse;
import com.anonymous.datahub.data_hub.interfaces.rest.mapper.PersonRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/person")
public class PersonController {

    private final CreatePersonUseCase createPersonUseCase;
    private final PersonRestMapper personRestMapper;

    public PersonController(CreatePersonUseCase createPersonUseCase, PersonRestMapper personRestMapper) {
        this.createPersonUseCase = createPersonUseCase;
        this.personRestMapper = personRestMapper;
    }

    @PostMapping
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody CreatePersonRequest request) {
        PersonResponse response = personRestMapper.toPersonResponse(
                createPersonUseCase.create(personRestMapper.toCreatePersonDto(request))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
