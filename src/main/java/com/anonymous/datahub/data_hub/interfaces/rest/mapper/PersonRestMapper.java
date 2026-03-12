package com.anonymous.datahub.data_hub.interfaces.rest.mapper;

import com.anonymous.datahub.data_hub.application.dto.CreatePersonDto;
import com.anonymous.datahub.data_hub.domain.model.Person;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.CreatePersonRequest;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.PersonResponse;
import org.springframework.stereotype.Component;

@Component
public class PersonRestMapper {

    public CreatePersonDto toCreatePersonDto(CreatePersonRequest request) {
        return new CreatePersonDto(request.name(), request.age());
    }

    public PersonResponse toPersonResponse(Person person) {
        return new PersonResponse(person.name(), person.age());
    }
}
