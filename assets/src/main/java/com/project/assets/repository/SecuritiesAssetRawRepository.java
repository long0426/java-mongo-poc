package com.project.assets.repository;

import com.project.assets.repository.document.SecuritiesAssetRawDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SecuritiesAssetRawRepository extends MongoRepository<SecuritiesAssetRawDocument, String> {

    Optional<SecuritiesAssetRawDocument> findFirstByCustomerIdOrderByFetchedAtDesc(String customerId);
}
