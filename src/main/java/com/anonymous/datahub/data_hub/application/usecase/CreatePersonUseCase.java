package com.anonymous.datahub.data_hub.application.usecase;

import com.anonymous.datahub.data_hub.application.dto.CreatePersonDto;
import com.anonymous.datahub.data_hub.domain.model.Person;

public interface CreatePersonUseCase {

    Person create(CreatePersonDto createPersonDto);
}
