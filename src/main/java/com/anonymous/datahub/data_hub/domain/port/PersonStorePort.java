package com.anonymous.datahub.data_hub.domain.port;

import com.anonymous.datahub.data_hub.domain.model.Person;

public interface PersonStorePort {

    Person save(Person person);
}
