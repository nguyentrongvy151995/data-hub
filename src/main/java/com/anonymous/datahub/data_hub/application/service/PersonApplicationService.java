package com.anonymous.datahub.data_hub.application.service;

import com.anonymous.datahub.data_hub.application.dto.CreatePersonDto;
import com.anonymous.datahub.data_hub.application.usecase.CreatePersonUseCase;
import com.anonymous.datahub.data_hub.domain.model.Person;
import com.anonymous.datahub.data_hub.domain.port.PersonStorePort;
import org.springframework.stereotype.Service;

@Service
public class PersonApplicationService implements CreatePersonUseCase {

    private final PersonStorePort personStorePort;

    public PersonApplicationService(PersonStorePort personStorePort) {
        this.personStorePort = personStorePort;
    }

    @Override
    public Person create(CreatePersonDto createPersonDto) {
        Person person = new Person(createPersonDto.name(), createPersonDto.age());
        return personStorePort.save(person);
    }
}
