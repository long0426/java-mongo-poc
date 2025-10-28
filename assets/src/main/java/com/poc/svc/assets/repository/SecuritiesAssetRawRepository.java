package com.poc.svc.assets.repository;

import com.poc.svc.assets.entity.SecuritiesAssetRawDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SecuritiesAssetRawRepository extends MongoRepository<SecuritiesAssetRawDocument, String> {

    Optional<SecuritiesAssetRawDocument> findFirstByCustomerIdOrderByFetchedAtDesc(String customerId);
}
