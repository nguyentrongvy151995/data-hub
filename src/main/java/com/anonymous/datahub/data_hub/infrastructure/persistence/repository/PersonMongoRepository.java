package com.anonymous.datahub.data_hub.infrastructure.persistence.repository;

import com.anonymous.datahub.data_hub.infrastructure.persistence.document.PersonDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PersonMongoRepository extends MongoRepository<PersonDocument, String> {
}
