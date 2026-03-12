package com.anonymous.datahub.data_hub.infrastructure.persistence.adapter;

import com.anonymous.datahub.data_hub.domain.model.Person;
import com.anonymous.datahub.data_hub.domain.port.PersonStorePort;
import com.anonymous.datahub.data_hub.infrastructure.persistence.document.PersonDocument;
import com.anonymous.datahub.data_hub.infrastructure.persistence.repository.PersonMongoRepository;
import org.springframework.stereotype.Component;

@Component
public class MongoPersonStoreAdapter implements PersonStorePort {

    private final PersonMongoRepository personMongoRepository;

    public MongoPersonStoreAdapter(PersonMongoRepository personMongoRepository) {
        this.personMongoRepository = personMongoRepository;
    }

    @Override
    public Person save(Person person) {
        PersonDocument document = new PersonDocument();
        document.setName(person.name());
        document.setAge(person.age());

        PersonDocument saved = personMongoRepository.save(document);
        return new Person(saved.getName(), saved.getAge());
    }
}
